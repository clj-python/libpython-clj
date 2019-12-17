(ns libpython-clj.iter-gen-seq-test
  "Iterators/sequences and such are crucial to handling lots of forms
  of data and thus they have to work correctly between the languages."
  (:require [libpython-clj.python :as py]
            [libpython-clj.require :refer [require-python]]
            [clojure.test :refer :all]))


(require-python '[builtins :as python])

(deftest generate-sequence-test
  (let [{{:strs [fortwice]} :globals}
        (py/run-simple-string "
def fortwice(xs):
    for x in xs:
        yield x
    for x in xs:
        yield x")]
    (is (= [1 2 3 4 5 6 7 8 9 10]
           (vec (fortwice  (python/map inc (range 10))))))  ;;=> [1 2 3 4 5 6 7 8 9 10]
    (is (= [1 2 3 4 5 6 7 8 9 10 1 2 3 4 5 6 7 8 9 10]
           (vec (fortwice (map inc (range 10)))))) ;;=> [1 2 3 4 5 6 7 8 9 10 1 2 3 4 5 6 7 8 9 10]
    (is (= [1 2 3 4 5 6 7 8 9 10 1 2 3 4 5 6 7 8 9 10]
           (vec (fortwice  (vec (python/map inc (range 10)))))))))
