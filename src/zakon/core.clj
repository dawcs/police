(ns zakon.core)

(def any ::any)
(def global-policy ::policy)
(def relations (atom (make-hierarchy)))
(def ^:dynamic *default-result* false)
(def ^:dynamic *policy* global-policy)
(def ^:dynamic *actor-dispatcher* identity)
(def ^:dynamic *action-dispatcher* identity)
(def ^:dynamic *subject-dispatcher* identity)

(defn ^:dynamic *dispatcher*
  [actor action subject]
  [(*actor-dispatcher* actor)
   (*action-dispatcher* action)
   (*subject-dispatcher* subject)])

(defn set-default-result! [res]
  (alter-var-root #'*default-result* (constantly res)))

(defn set-dispatcher! [f]
  (alter-var-root #'*dispatcher* (constantly f)))

(defmulti dispatch
  (fn [policy actor action subject] [policy actor action subject])
  :hierarchy relations)

(defmethod dispatch [global-policy any any any]
  [_ _ _ _] {:result *default-result*
             :source ::default-rule})

(defn- known-entity? [entity]
  (or (= entity any)
      (contains? (descendants @relations any) entity)))

(defn- known-policy? [policy]
  (or (= policy global-policy)
      (contains? (descendants @relations global-policy) policy)))

(defn- register-entity! [entity]
  (let [ns (namespace entity)
        str-any (name any)
        entity-any (keyword ns str-any)]
    (when-not (known-entity? entity-any)
      (swap! relations derive entity-any any))
    (swap! relations derive entity entity-any)))

(defn- register-policy! [policy]
  (swap! relations derive policy global-policy))

(defn- entity-name [v]
  (if (keyword? v)
    (name v)
    (-> v str (clojure.string/replace #"\s" "-"))))

(defn build-entity
  ([value]
   (let [[kw ns]
         (if (keyword? value)
           [(name value) (or (namespace value) (str *ns*))]
           [(-> value str (clojure.string/replace #"\s" "-"))
            (-> value class str (clojure.string/split #"\s") last)])]
     (keyword ns (name kw))))
  ([domain value]
   (keyword (entity-name domain) (entity-name value))))

(defn inherit! [child parent]
  (let [kw-child (build-entity child)
        kw-parent (build-entity parent)
        _ (when-not (known-entity? kw-parent) (register-entity! kw-parent))]
    (swap! relations derive kw-child kw-parent)))

(defn inherited? [child parent]
  (let [kw-child (build-entity child)
        kw-parent (build-entity parent)]
    (-> @relations
        (descendants kw-parent)
        (contains? kw-child))))

(defn extract [result actor action subject]
  (cond
    (ifn? result) (result {:actor actor :action action :subject subject})
    (instance? clojure.lang.Atom result) (extract @result actor action subject)
    :else result))

(defn can?
  ([actor action subject]
   (can? *policy* actor action subject))
  ([policy actor action subject]
   (let [[actor' action' subject'] (*dispatcher* actor action subject)
         kw-actor (build-entity actor')
         kw-action (build-entity action')
         kw-subject (build-entity subject')
         kw-policy (build-entity policy)
         _ (when-not (known-entity? kw-actor) (register-entity! kw-actor))
         _ (when-not (known-entity? kw-action) (register-entity! kw-action))
         _ (when-not (known-entity? kw-subject) (register-entity! kw-subject))
         _ (when-not (known-policy? kw-policy) (register-policy! kw-policy))
         {:keys [result]} (dispatch kw-policy kw-actor kw-action kw-subject)]
     (extract result actor action subject))))

(def cant? (complement can?))

(defn find-rule
  ([actor action subject]
   (find-rule *policy* actor action subject))
  ([policy actor action subject]
   (let [[actor' action' subject'] (*dispatcher* actor action subject)
         kw-actor (build-entity actor')
         kw-action (build-entity action')
         kw-subject (build-entity subject')
         kw-policy (build-entity policy)
         _ (when-not (known-entity? kw-actor) (register-entity! kw-actor))
         _ (when-not (known-entity? kw-action) (register-entity! kw-action))
         _ (when-not (known-entity? kw-subject) (register-entity! kw-subject))
         _ (when-not (known-policy? kw-policy) (register-policy! kw-policy))
         {:keys [source]} (dispatch kw-policy kw-actor kw-action kw-subject)]
     source)))

(defmacro defrule
  ([rule res]
   (let [m (meta &form)]
     `~(with-meta
         `(defrule *policy* ~rule ~res)
         m)))
  ([policy [actor action subject] res]
   (let [m (meta &form)
         ns (str *ns*)]
    `(let [kw-actor# (build-entity ~actor)
           kw-action# (build-entity ~action)
           kw-subject# (build-entity ~subject)
           kw-policy# (build-entity ~policy)
           new-rule# [kw-policy# kw-actor# kw-action# kw-subject#]
           prev-rules# (-> dispatch prefers keys set (conj any))]
       (defmethod dispatch [kw-policy# kw-actor# kw-action# kw-subject#]
         ~'[_ _ _ _]
         {:result ~res
          :source (assoc ~m :ns ~ns)})
       (when-not (contains? prev-rules# new-rule#)
         (doseq [rule# prev-rules#]
           (prefer-method dispatch new-rule# rule#)))
       {::policy kw-policy#
        ::actor kw-actor#
        ::action kw-action#
        ::subject kw-subject#}))))

(defmacro can!
  ([actor action subject]
   (let [m (meta &form)]
     `~(with-meta
         `(can! *policy* ~actor ~action ~subject)
         m)))
  ([policy actor action subject]
   (let [m (meta &form)]
     `~(with-meta
         `(defrule ~policy [~actor ~action ~subject] true)
         m))))

(defmacro cant!
  ([actor action subject]
   (let [m (meta &form)]
     `~(with-meta
         `(cant! *policy* ~actor ~action ~subject)
         m)))
  ([policy actor action subject]
   (let [m (meta &form)]
     `~(with-meta
         `(defrule ~policy [~actor ~action ~subject] false)
         m))))

(defmacro with-policy [policy & body]
  (let [m (meta &form)]
    `(binding [*policy* ~policy]
       ~(with-meta
          `(do ~@body)
          m))))

(defmacro cleanup! []
  `(do
     (reset! relations (make-hierarchy))
     (def ~'dispatch nil)
     (defmulti ~'dispatch
       (fn ~'[policy actor action subject] ~'[policy actor action subject])
       :hierarchy relations)
     (defmethod ~'dispatch ~'[global-policy any any any]
       ~'[_ _ _ _] {:result *default-result*
                    :source ::default-rule})))
