(ns libpython-clj.python.numpy-test
  (:require [libpython-clj.python :as py]
            [tech.v2.datatype :as dtype]
            [tech.v2.datatype.functional :as dfn]
            [tech.v2.tensor :as dtt]
            [clojure.test :refer :all]))

(py/initialize!)


;;The basic math operations of the dfn namespace must work on numpy objects.
(deftest basic-numpy
  (let [np-mod (py/import-module "numpy")
        test-data [[1 2] [3 4]]
        np-ary (py/$a np-mod array test-data)
        tens (dtt/->tensor test-data)]
    (is (dfn/equals (-> (dfn/- np-ary 4)
                        (py/as-tensor))
                    (dfn/- tens 4)))
    (is (dfn/equals [1 2 3 4]
                    (dtype/make-container :java-array :int64
                                          (dtype/->reader np-ary))))))
