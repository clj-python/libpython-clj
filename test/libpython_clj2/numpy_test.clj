(ns libpython-clj2.numpy-test
  (:require [clojure.test :refer [deftest is]]
            [libpython-clj2.python :as py]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [tech.v3.tensor :as dtt]))

(py/initialize!)

(def np-mod (py/import-module "numpy"))


;;The basic math operations of the dfn namespace must work on numpy objects.
(deftest basic-numpy
  (let [test-data [[1 2] [3 4]]
        np-ary (py/$a np-mod array test-data)
        tens (dtt/->tensor test-data)]
    (is (dfn/equals (dtt/ensure-tensor np-ary) tens))
    (is (dfn/equals [1 2 3 4]
                    (dtype/make-container :java-array :int64 np-ary)))))
