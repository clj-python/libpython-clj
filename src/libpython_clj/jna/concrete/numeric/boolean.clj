(ns libpython-clj.jna.concrete.numeric.boolean
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     find-pylib-symbol]
             :as libpy-base]
            [tech.jna :as jna])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyObject]))


(def-pylib-fn PyBool_Check
  "Return true if o is of type PyBool_Type."
  Integer
  [p ensure-pyobj])


(defn Py_True
  "The Python True object. This object has no methods. It needs to be treated just like
  any other object with respect to reference counts."
  ^Pointer []
  (find-pylib-symbol "_Py_TrueStruct"))


(defn Py_False
  "The Python False object. This object has no methods. It needs to be treated just like
  any other object with respect to reference counts."
  ^Pointer []
  (find-pylib-symbol "_Py_FalseStruct"))


(def-pylib-fn PyBool_FromLong
  "Return value: New reference.

   Return a new reference to Py_True or Py_False depending on the truth value of v."
  Pointer
  [v int])
