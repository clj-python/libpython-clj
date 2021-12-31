(ns libpython-clj2.java-api-test
  (:require [libpython-clj2.java-api :as japi]
            [libpython-clj2.python.ffi :as py-ffi]
            [tech.v3.datatype.jvm-map :as jvm-map]
            [clojure.test :refer [deftest is]]))


(deftest base-japi-test
  (japi/-initialize nil)
  (let [rs-retval (japi/-runString "data = 5")
        globals (get rs-retval "globals")]
    (is (= 5 (get globals "data")))
    (japi/-setItem globals "data" 6)
    (is (= 6 (get globals "data"))))
  (is (= 0 (py-ffi/PyGILState_Check)))
  (let [np (japi/-importModule "numpy")
        ones-fn (japi/-getAttr np "ones")
        ;;also works with int-array
        base-data (ones-fn (java.util.ArrayList. [2 3]))
        jvm-data (japi/-arrayToJVM base-data)]
    (is (= "float64" (get jvm-data "datatype")))
    (is (= (vec (repeat 6 1.0))
           (vec (get jvm-data "data"))))
    (is (= 0 (py-ffi/PyGILState_Check)))
    (let [base-data (japi/-callKw ones-fn [[2 3]] {"dtype" "int32"})
          jvm-data (japi/-arrayToJVM base-data)]
          (is (= "int32" (get jvm-data "datatype")))
          (is (= (vec (repeat 6 1))
                 (vec (get jvm-data "data"))))
          (is (= 0 (py-ffi/PyGILState_Check)))))
  (let [gilstate (japi/-lockGIL)]
    (try
      (let [test-fn (-> (japi/-runString "def calcSpread(bid,ask):\n\treturn bid-ask\n\n")
                        (get "globals")
                        (get "calcSpread"))
            call-ctx (japi/-allocateFastcallContext)
            _ (println test-fn)
            n-calls 100000
            start-ns (System/nanoTime)
            _ (is (= -1  (japi/-fastcall call-ctx test-fn 1 2)))
            _ (dotimes [iter n-calls]
                (japi/-fastcall call-ctx test-fn 1 2))
            end-ns (System/nanoTime)
            ms (/ (- end-ns start-ns) 10e6)]
        (japi/-releaseFastcallContext call-ctx)
        (println "Python fn calls/ms" (/ n-calls ms)))
      (finally
        (japi/-unlockGIL gilstate)))))
