(ns libpython-clj2.fncall-test
  (:require [libpython-clj2.python :as py]
            [clojure.test :refer :all]))


(py/initialize!)

(deftest complex-fn-test
  (println "1")
  (let [testmod (py/import-module "testcode")
        _ (println "2")
        testcases (py/py.- testmod complex_fn_testcases)]
    (println "3")
    (is (= (-> (get testcases "complex_fn(1, 2, c=10, d=10, e=10)")
               (py/->jvm))
           (-> (py/$a testmod complex_fn 1 2 :c 10 :d 10 :e 10)
               (py/->jvm))))
    (println "4")
    (is (= (-> (get testcases "complex_fn(1, 2, 10, 11, 12, d=10, e=10)")
               (py/->jvm))
           (-> (py/$a testmod complex_fn 1 2 10 11 12 :d 10 :e 10)
               (py/->jvm))))
    (println "5")
    (is (= (-> (get testcases "complex_fn(1, 2, c=10, d=10, e=10)")
               (py/->jvm))
           (-> (apply py/afn testmod "complex_fn" [1 2 :c 10 :d 10 :e 10])
               (py/->jvm))))
    (println "6")
    (is (= (-> (get testcases "complex_fn(1, 2, 10, 11, 12, d=10, e=10)")
               (py/->jvm))
           (-> (apply py/afn testmod "complex_fn" [1 2 10 11 12 :d 10 :e 10])
               (py/->jvm))))))
