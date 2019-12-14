(ns libpython-clj.require-python-test
  (:require [libpython-clj.require :refer [require-python]]
            [libpython-clj.python :as py]
            [clojure.test :refer :all]))


;;Since this test mutates the global environment we have to accept that
;;it may not always work.

(require-python '[math
                  :refer :*
                  :exclude [sin cos]
                  :as pymath])


(deftest base-require-test
  (let [publics (ns-publics (find-ns 'libpython-clj.require-python-test))]
    (is (contains? publics 'acos))
    (is (contains? publics 'floor))
    (is (not (contains? publics 'sin)))
    (is (= 10.0 (double (floor 10.1))))
    (is (= pymath (py/import-module "math")))
    (is (thrown? Throwable (require-python '[math :refer [blah]])))))
