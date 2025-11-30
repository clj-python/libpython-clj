(ns hooks.libpython-clj.python.py-dot-dot
  (:require [clj-kondo.hooks-api :as api]))

(defn py..
  "Transform py.. to just evaluate the object, ignoring method/attribute symbols.
   (py.. obj method arg) -> obj"
  [{:keys [node]}]
  (let [children (:children node)
        obj-node (second children)]
    (if obj-node
      {:node obj-node}
      {:node (api/token-node nil)})))
