(ns libpython-clj.jna.concrete.code
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyObject]))


(def-pylib-fn PyCode_Check
  "Return true if co is a code object."
  Integer
  [co ensure-pyobj])


(def-pylib-fn PyCode_GetNumFree
  "Return the number of free variables in co."
  Integer
  [co ensure-pyobj])



(def-pylib-fn PyCode_NewEmpty
  "Return a new empty code object with the specified filename, function name, and first
  line number. It is illegal to exec or eval() the resulting code object."
  Pointer
  [filename str]
  [funcname str]
  [firstlineno int])
