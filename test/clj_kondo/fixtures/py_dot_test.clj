(ns clj-kondo.fixtures.py-dot-test
  (:require [libpython-clj2.python :refer [py. py..]]))

(defn test-py-dot []
  (let [obj {:foo "bar"}]
    (py. obj method arg1 arg2)
    (py.. obj method1 method2 method3)))
