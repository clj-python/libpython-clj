(ns libpython-clj.py-modules.numpy
  (:require [libpython-clj.python :as py]
            [libpython-clj.export-module-symbols
             :refer [export-module-symbols]]))


(export-module-symbols "numpy")


(comment
  (linspace 2 3)
  (py/call-kw linspace [2 3] {:num 5})
  )
