(ns clj-kondo.fixtures.py-dot-test
  (:require [libpython-clj2.python :refer [py. py.. py.- py* py**]]))

(defn test-py-macros []
  (let [obj {:foo "bar"}]
    (py. obj method arg1 arg2)
    (py.. obj method1 method2 method3)
    (py.- obj attribute)
    (py* callable [arg1 arg2])
    (py** callable [arg1] {:kwarg1 val1})))
