(ns libpython-clj.jna-test
  (:require [clojure.test :refer :all]
            [libpython-clj.jna :as libpy]))


(deftest print-test
  (libpy/Py_InitializeEx 0)
  (libpy/PyRun_SimpleString
"from time import time,ctime
print('Today is', ctime(time()))
")
  (let [finalize-val (libpy/Py_FinalizeEx)]
    (println finalize-val)))
