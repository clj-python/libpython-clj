(ns code.codegen
  (:require [libpython-clj2.python :as py]
            [libpython-clj2.codegen :as cgen]))


(defn codegen
  []
  (py/initialize!)
  (cgen/write-namespace! "numpy"))
