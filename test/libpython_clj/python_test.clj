(ns libpython-clj.python-test
  (:require [libpython-clj.python :as py]
            [tech.v2.datatype :as dtype]
            [tech.v2.tensor :as dtt]
            [clojure.test :refer :all])
  (:import [java.io StringWriter]
           [java.util Map List]))


(deftest stdout-and-stderr
  (py/initialize!)
  (is (= "hey\n" (with-out-str
                   (py/run-simple-string "print('hey')"))))
  (is (= "hey\n" (let [custom-writer (StringWriter.)]
                   (with-bindings {#'*err* custom-writer}
                     (py/run-simple-string "import sys\nprint('hey', file=sys.stderr)"))
                   (.toString custom-writer))))
  ;;Python exceptions get translated into actual java exceptions.
  (is (thrown? Throwable (py/run-simple-string "import sys\nprint('hey', stderr"))))


(deftest dicts
  (py/initialize!)
  (let [py-dict (py/->python {:a 1 :b 2})]
    (is (= :dict (py/python-type py-dict)))
    (is (= 2 (-> (py/call-attr py-dict "__len__")
                 (py/->jvm))))
    (is (= {"a" 1 "b" 2}
           (->> (py/->jvm py-dict)
                (into {}))))
    (let [bridge-dict (py/as-jvm py-dict)]
      (is (instance? Map bridge-dict))
      (is (= #{"a" "b"} (->> (keys bridge-dict)
                             set)))
      (is (= #{1 2} (->> (vals bridge-dict)
                         set)))
      (is (= {"a" 1 "b" 2}
             (into {} bridge-dict))))))


(deftest lists
  (py/initialize!)
  (let [py-list (py/->py-list [4 3 2 1])]
    (is (= :list (py/python-type py-list)))
    (is (= 4 (-> (py/call-attr py-list "__len__")
                 (py/->jvm))))
    (is (= [4 3 2 1]
           (->> (py/->jvm py-list)
                (into []))))

    (let [bridge-list (py/as-jvm py-list)]
      (is (instance? List bridge-list))
      (is (= [4 3 2 1] (into [] bridge-list)))
      ;;This actually calls the python sort function.
      (.sort ^List bridge-list nil)
      (is (= [1 2 3 4] (into [] bridge-list))))))


(deftest global-dict
  (py/initialize!)
  (let [main-module (py/add-module "__main__")
        ^Map globals (-> (py/module-dict main-module)
                    (py/as-jvm))]
    (is (instance? Map globals))
    (.put globals "item" 100)
    (py/set-item! globals "item2" 200)
    ;;During run-simple-string, if nothing else is specified the
    ;;global map is used as a local map.
    (py/run-simple-string "item3 = item + item2")
    (is (= 300 (globals "item3")))))


(deftest numpy-and-back
  (py/initialize!)
  (let [jvm-tens (dtt/->tensor (->> (range 9)
                                    (partition 3)))]
    ;;zero-copy can't work on jvm datastructures with current JNA tech.
    ;;IT would require 'pinning' which isn't yet available.
    (is (nil? (py/as-numpy jvm-tens)))
    (let [py-tens (py/->numpy jvm-tens)]
      (is (= [[0.0 1.0 2.0] [3.0 4.0 5.0] [6.0 7.0 8.0]]
             (-> (py/as-tensor py-tens)
                 dtt/->jvm)))
      ;;This operation is in-place
      (let [py-trans (py/call-attr py-tens "transpose" [1 0])]
        (is (= [[0.0 1.0 2.0] [3.0 4.0 5.0] [6.0 7.0 8.0]]
             (-> (py/as-tensor py-tens)
                 dtt/->jvm)))
        (is (= [[0.0 3.0 6.0] [1.0 4.0 7.0] [2.0 5.0 8.0]]
             (-> (py/as-tensor py-trans)
                 dtt/->jvm)))
        ;;But they are sharing backing store, so mutation will travel both
        ;;ways.  Creepy action at a distance indeed
        (dtype/copy! [5 6 7] (py/as-tensor py-trans))
        (is (= [[5.0 1.0 2.0] [6.0 4.0 5.0] [7.0 7.0 8.0]]
               (-> (py/as-tensor py-tens)
                   dtt/->jvm))))
      (let [main-module (py/add-module "__main__")
            ^Map globals (-> (py/module-dict main-module)
                             (py/as-jvm))]
        (py/set-item! globals "np_ary" py-tens)
        (py/run-simple-string "np_ary[2,2] = 100")
        (is (= [[5.0 1.0 2.0] [6.0 4.0 5.0] [7.0 7.0 100.0]]
               ;;zero copy almost always works the other way, however.  So there is
               ;;py/->tensor available.  Copying the numpy object will allow the
               ;;zero copy pathway to work.
               (-> (py/as-tensor py-tens)
                   dtt/->jvm)))))))


(deftest numpy-scalars
  (py/initialize!)
  (let [np (py/import-module "numpy")
        scalar-constructors (concat ["float64"
                                     "float32"]
                                    (for [int-type ["int" "uint"]
                                          int-width [8 16 32 64]]
                                      (str int-type int-width)))]
    (doseq [constructor scalar-constructors]
      (is (= 3.0 (-> (py/call-attr np constructor 3.0)
                     double))
          (str "Item type " constructor)))))


(deftest dict-with-complex-key
  (py/initialize!)
  (let [py-dict (py/->python {["a" "b"] 1
                              ["c" "d"] 2})
        bridged (py/as-jvm py-dict)]
    (is (= #{["a" "b"]
             ["c" "d"]}
           (->> (keys bridged)
                ;;Bridged tuples are lists, not persistent vectors.
                (map vec)
                set)))))


(deftest simple-print-crashed
  (py/initialize!)
  (let [numpy (py/import-module "numpy")]
    (println (py/as-tensor (py/call-attr numpy "ones" [3 3])))))
