(ns libpython-clj.python-test
  (:require [libpython-clj.python :as py]
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
    (py/run-simple-string "item3 = item + item2")
    (is (= 300 (globals "item3")))))
