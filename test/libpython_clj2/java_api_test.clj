(ns libpython-clj2.java-api-test
  (:require [libpython-clj2.java-api :as japi]
            [tech.v3.datatype.jvm-map :as jvm-map]
            [clojure.test :refer [deftest is]]))


(deftest base-japi-test
  (japi/-initialize nil)
  (let [rs-retval (japi/-runString "data = 5")
        globals (get rs-retval "globals")]
    (is (= 5 (get globals "data"))))
  (let [np (japi/-importModule "numpy")
        ones-fn (japi/-getAttr np "ones")
        ;;also works with int-array
        base-data (ones-fn (java.util.ArrayList. [2 3]))
        jvm-data (japi/-arrayToJVM base-data)]
    (is (= "float64" (get jvm-data "datatype")))
    (is (= (vec (repeat 6 1.0))
           (vec (get jvm-data "data"))))
    (let [base-data (japi/-callKw ones-fn [[2 3]] {"dtype" "int32"})
          jvm-data (japi/-arrayToJVM base-data)]
          (is (= "int32" (get jvm-data "datatype")))
          (is (= (vec (repeat 6 1))
                 (vec (get jvm-data "data")))))))
