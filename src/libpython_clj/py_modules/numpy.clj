(ns libpython-clj.py-modules.numpy
  (:require [libpython-clj.python :as py]
            [libpython-clj.require :refer [require-python]])
  (:refer-clojure :exclude [test take str sort short require repeat partition
                            mod min max long load int identity float empty double
                            conj char cast byte]))


(require-python '[numpy :refer :* :reload])


(comment
  (linspace 2 3)
  (py/call-kw linspace [2 3] {:num 5})
  )
