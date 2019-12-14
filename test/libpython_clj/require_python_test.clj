(ns libpython-clj.require-python-test
  (:require [libpython-clj.require :as req :refer [require-python]]
            [clojure.test :refer :all]))


;;Since this test mutates the global environment we have to accept that
;;it may not always work.

(require-python '[math
                  :refer :*
                  :exclude [sin cos]])


(deftest base-require-test
  (let [publics (ns-publics (find-ns 'libpython-clj.require-python-test))]
    (is (contains? publics 'acos))
    (is (contains? publics 'floor))
    (is (not (contains? publics 'sin)))
    (is (= 10.0 (double (floor 10.1))))
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
    (is (= #{:a} (parse-flags #{:a :b} '[:a  false :b true])))
    (is (= #{:a} (parse-flags #{:a :b} '[:b true :a false])))
    (is (= #{:a} (parse-flags #{:a :b} '[:b :a false])))
    (is (= #{:a} (parse-flags #{:a :b} '[:b :a false :a true])))))

