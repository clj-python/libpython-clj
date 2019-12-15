(ns libpython-clj.require-python-test
  (:require [libpython-clj.require :as req :refer [require-python]
             :reload true]
            [libpython-clj.python :as py]
            [clojure.test :refer :all]))

;; Since this test mutates the global environment we have to accept that
;; it may not always work.

(require-python '[math
                  :refer :*
                  :exclude [sin cos]
                  :as pymath
                  :reload true])


(deftest base-require-test
  (let [publics (ns-publics (find-ns 'libpython-clj.require-python-test))]
    (is (contains? publics 'acos))
    (is (contains? publics 'floor))
    (is (not (contains? publics 'sin)))
    (is (= 10.0 (double (floor 10.1))))
    (is (= pymath (py/import-module "math")))
    (is (thrown? Throwable (require-python '[math :refer [blah]])))))

(deftest parse-flags-test
  ;; sanity check
  (is (= #{:reload :foo}
         #{:foo :reload}))
  (let [parse-flags #'req/parse-flags]
    (is (= #{:reload} (parse-flags
                       #{:reload}
                       '[:reload foo])))
    (is (= #{} (parse-flags #{} '[:reload foo])))
    (is (= #{:reload} (parse-flags #{:reload} '[:reload true])))
    (is (= #{:reload} (parse-flags #{:reload} '[:reload :as foo])))
    (is (= #{:reload} (parse-flags #{:reload} '[:reload foo :as])))
    (is (= #{:reload} (parse-flags #{:reload} '[foo :reload :as bar])))
    (is (= #{} (parse-flags #{:reload} '[:reload false])))
    (is (= #{} (parse-flags #{:reload} '[:reload false :reload])))
    (is (= #{} (parse-flags #{:reload} '[:reload false :reload true])))
    (is (= #{:reload} (parse-flags  #{:reload} '[:reload :reload false])))
    (is (= #{:reload} (parse-flags #{:reload} '[:reload true :reload false])))
    (is (= #{:a :b}) (parse-flags #{:a :b} '[:a true :b]))
    (is (= #{:a :b}) (parse-flags #{:a :b} '[:a :b]))
    (is (= #{:a :b} (parse-flags #{:a :b} '[:a true :b])))
    (is (= #{:a :b} (parse-flags #{:a :b} '[:a  :b true])))
    (is (= #{:a :b} (parse-flags #{:a :b} '[:a  true :b true])))
    (is (= #{:b} (parse-flags #{:a :b} '[:a  false :b true])))
    (is (= #{:b} (parse-flags #{:a :b} '[:b true :a false])))
    (is (= #{:b} (parse-flags #{:a :b} '[:b :a false])))
    (is (= #{:b} (parse-flags #{:a :b} '[:b :a false :a true])))))

(deftest python-lib-configuration-test
  (let [python-lib-configuration #'req/python-lib-configuration
        simple-req               '[csv]
        simple-spec              (python-lib-configuration
                                  simple-req
                                  (find-ns 'libpython-clj.require-python-test))
        {:keys [exclude
                supported-flags
                current-ns-sym
                module-name
                module-name-or-ns
                reload?
                no-arglists?
                etc
                current-ns
                this-module
                python-namespace
                refer]}          simple-spec
        csv-module               (py/import-module "csv")]

    ;; no exclusions
    (is (= #{} exclude))
    (is (= #{:no-arglists :reload :alpha-load-ns-classes}
           supported-flags))
    (is (= 'libpython-clj.require-python-test
           current-ns-sym
           (symbol (str current-ns))))
    (is (= 'csv module-name module-name-or-ns))
    (is (nil? reload?))
    (is (nil? no-arglists?))
    (is (= {} etc))
    (is (= csv-module this-module)))


  (let [python-lib-configuration #'req/python-lib-configuration
        simple-req               '[requests
                                   :reload true
                                   :refer [get]
                                   :no-arglists]
        simple-spec              (python-lib-configuration
                                  simple-req
                                  (find-ns 'libpython-clj.require-python-test))
        {:keys [exclude
                supported-flags
                current-ns-sym
                module-name
                module-name-or-ns
                reload?
                no-arglists?
                etc
                current-ns
                this-module
                python-namespace
                refer]}          simple-spec
        requests-module          (py/import-module "requests")]

    ;; no exclusions
    (is (= #{} exclude))
    (is (= 'libpython-clj.require-python-test
           current-ns-sym
           (symbol (str  current-ns))))
    (is (= 'requests module-name module-name-or-ns))
    (is (= reload? :reload))
    (is (= no-arglists? :no-arglists))
    (is (= {:refer '[get]} etc))
    (is (= requests-module this-module))
    (is (= #{'get} refer))
    (is (nil? python-namespace))))
