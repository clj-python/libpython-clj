(ns libpython-clj.jna.concrete.tuple
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.jna :as jna])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyObject]))


(def-pylib-fn PyTuple_Check
  "Return true if p is a tuple object or an instance of a subtype of the tuple type."
  Integer
  [p ensure-pyobj])


(def-pylib-fn PyTuple_New
  "Return value: New reference.

   Return a new tuple object of size len, or NULL on failure."
  PyObject
  [len jna/size-t])


(def-pylib-fn PyTuple_GetItem
  "Return value: Borrowed reference.

   Return the object at position pos in the tuple pointed to by p. If pos is out of
   bounds, return NULL and sets an IndexError exception."
  PyObject
  [p ensure-pyobj]
  [pos jna/size-t])


(def-pylib-fn PyTuple_GetSlice
  "Return value: New reference.

   Take a slice of the tuple pointed to by p from low to high and return it as a new
   tuple."
  PyObject
  [p ensure-pyobj]
  [low jna/size-t]
  [high jna/size-t])


(def-pylib-fn PyTuple_SetItem
  "Insert a reference to object o at position pos of the tuple pointed to by p. Return 0
  on success.

   Note

   This function “steals” a reference to o"
  Integer
  [p ensure-pyobj]
  [pos jna/size-t]
  [o ensure-pyobj])
