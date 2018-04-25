# zakon

> But whoso looketh into the perfect law of liberty, and continueth therein, he being not a forgetful hearer, but a doer of the work, this man shall be blessed in his deed.
> (James 1:25)

zakon (/zakon/ rus. *закон - law*) is declarative authorization library inspired by https://github.com/ryanb/cancan
It has no dependencied (despite clojure itself) and uses clojure multimethods under the hood.Everything is highly experimental.

## Usage

**Not released to clojars yet**

```clojure
(:require '[zakon.core :refer [can! cant! can? any]])

(can? :i :go :home) ;; => false
(can! :i :go :home)
(can? :i :go :home) ;; => true
(cant! any :go :home)
(can? :boss :go :home) ;; => false
(can? :i :go :home) ;; => true
```

More detailed docs and examples soon...

## License

Copyright © 2018 DAWCS

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
