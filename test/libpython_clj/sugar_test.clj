(ns libpython-clj.sugar-test
  (:require [libpython-clj.sugar :as pysug :reload true]
            [clojure.test :refer :all]
            [libpython-clj.python :as py]))

(deftest test-pyxfn
  (let [{{:strs [addem addx addxkwargs stringify addbang]} :globals}
        (py/run-simple-string
         "
def addem(nums):
    for num in nums:
        yield num + num


def addx(nums, x):
    for num in nums:
        yield num + x


def addxkwargs(nums, *, x=None):
    if x is None:
        raise Exception('x should not be None')
    for num in nums:
        yield num + x


def stringify(xs):
    for x in xs:
        yield str(x)


def addbang(xs):
    for x in xs:
        yield '{}!'.format(x)
")]
    (is (= [0 2 4 6 8 10 12 14 16 18]
           (into [] (pysug/pyxfn addem) (range 10))))

    (is (= [1 2 3 4 5]
           (into [] (pysug/pyxfn addx 1) (range 5))))

    (is (= [1 2 3 4 5]
           (into [] (pysug/pyxfn addxkwargs :x 1) (range 5))))

    (is (= ["00!" "11!" "22!" "33!"]
           (transduce
            (comp
             (pysug/pyxfn stringify)
             (pysug/pyxfn addem)
             (pysug/pyxfn addbang))
            (completing conj)
            []
            (range 4)))) (is (= ["33!" "22!" "11!" "00!"]
                                (transduce
                                 (comp
                                  (pysug/pyxfn stringify)
                                  (pysug/pyxfn addem)
                                  (pysug/pyxfn addbang))
                                 (fn
                                   ([result] ((comp vec reverse) result))
                                   ([result input]
                                    (conj result input)))
                                 []
                                 (range 4))))

    (is (= [["22!" "33!"] ["00!" "11!"]]
           (transduce
            (comp
             (pysug/pyxfn stringify)
             (pysug/pyxfn addem)
             (pysug/pyxfn addbang)
             (partition-all 2))
            (fn
              ([result] ((comp vec reverse) result))
              ([result input]
               (conj result input)))
            []
            (range 4))))))

