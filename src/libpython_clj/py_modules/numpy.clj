(ns libpython-clj.py-modules.numpy
  (:require [libpython-clj.python :as py]
            [libpython-clj.export-module-symbols
             :refer [export-module-symbols]])
  (:refer-clojure :exclude [test take str sort short require repeat partition
                            mod min max long load int identity float empty double
                            conj char cast byte]))


(export-module-symbols "numpy")


(comment
  (linspace 2 3)
  (py/call-kw linspace [2 3] {:num 5})
  )
