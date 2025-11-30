(ns hooks.libpython-clj.python.py-macros
  (:require [clj-kondo.hooks-api :as api]))

(defn py-macro
  "Transform py macros to just evaluate the object, ignoring method/attribute symbols.
   (py. obj method arg) -> obj
   (py.. obj method1 method2) -> obj
   (py.- obj attr) -> obj
   (py* callable args) -> callable
   (py** callable args kwargs) -> callable"
  [{:keys [node]}]
  (let [children (:children node)
        obj-node (second children)]
    (if obj-node
      {:node obj-node}
      {:node (api/token-node nil)})))
