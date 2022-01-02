(ns libpython-clj2.java-api-test
  (:require [libpython-clj2.java-api :as japi]
            [libpython-clj2.python.ffi :as py-ffi]
            [tech.v3.datatype.jvm-map :as jvm-map]
            [clojure.test :refer [deftest is]]))


(deftest base-japi-test
  (japi/-initialize nil)
  (let [gilstate (japi/-lockGIL)]
    (japi/-runStringAsFile "data = 5")
    (is (= 5 (japi/-getGlobal "data")))
    (japi/-setGlobal "data" 6)
    (is (= 6 (japi/-getGlobal "data")))

    (let [np (japi/-importModule "numpy")
          ones-fn (japi/-getAttr np "ones")
          ;;also works with int-array
          base-data (ones-fn (java.util.ArrayList. [2 3]))
          jvm-data (japi/-arrayToJVM base-data)]
      (is (= "float64" (get jvm-data "datatype")))
      (is (= (vec (repeat 6 1.0))
             (vec (get jvm-data "data"))))
      (let [base-data (japi/-callKw ones-fn [[2 3]] {"dtype" "int32"})
            jvm-data (japi/-arrayToJVM base-data)
            data-ary (get jvm-data "data")]
        (is (= "int32" (get jvm-data "datatype")))
        (is (= (vec (repeat 6 1))
               (vec data-ary)))
        (java.util.Arrays/fill ^ints data-ary 25)
        (japi/-copyData data-ary base-data)
        (let [jvm-data (japi/-arrayToJVM base-data)]
          (is (= (vec (repeat 6 25))
                 (vec (get jvm-data "data")))))))
    (try
      (let [test-fn (-> (japi/-runStringAsFile "def calcSpread(bid,ask):\n\treturn bid-ask\n\n")
                        (get "calcSpread"))
            n-calls 100000]
        (let [start-ns (System/nanoTime)
              _ (dotimes [iter n-calls]
                  (japi/-call test-fn 1 2))
              end-ns (System/nanoTime)
              ms (/ (- end-ns start-ns) 10e6)]
          (println "Python fn calls/ms" (/ n-calls ms)))
        ;;low level api
        (let [call-ctx (japi/-allocateFastcallContext)
              _ (is (= -1  (japi/-fastcall call-ctx test-fn 1 2)))
              start-ns (System/nanoTime)
              _ (dotimes [iter n-calls]
                  (japi/-fastcall call-ctx test-fn 1 2))
              end-ns (System/nanoTime)
              ms (/ (- end-ns start-ns) 10e6)]
          (japi/-releaseFastcallContext call-ctx)
          (println "Python fastcall calls/ms" (/ n-calls ms)))
        ;;high level api
        (with-open [fcall (japi/-makeFastcallable test-fn)]
          (is (= -1 (japi/-call fcall 1 2)))
          (let [start-ns (System/nanoTime)
                _ (dotimes [iter n-calls]
                    (japi/-call fcall 1 2))
                end-ns (System/nanoTime)
                ms (/ (- end-ns start-ns) 10e6)]
            (println "Python fastcallable calls/ms" (/ n-calls ms))))
        ;;Global setitem pathway
        (japi/-setGlobal "bid" 1)
        (japi/-setGlobal "ask" 2)
        (is (= -1 (japi/-runStringAsInput "bid-ask")))
        (let [start-ns (System/nanoTime)
              _ (dotimes [iter n-calls]
                  (japi/-setGlobal "bid" 1)
                  (japi/-setGlobal "ask" 2)
                  (japi/-runStringAsInput "bid-ask"))
              end-ns (System/nanoTime)
              ms (/ (- end-ns start-ns) 10e6)]
          (println "Python setglobal pathway calls/ms" (/ n-calls ms))))
      (finally
        (japi/-unlockGIL gilstate)))))


(defn only-string-input
  []
  (let [gilstate (japi/-lockGIL)
        n-calls 5000000]
    (try
      (dotimes [iter n-calls]
        (japi/-setGlobal "bid" 1.1)
        (japi/-setGlobal "ask" 2.2)
        (japi/-runStringAsInput "bid-ask"))
      (finally
        (japi/-unlockGIL gilstate)))))
