(ns libpython-clj.jna.concrete.tuple
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.v3.jna :as jna])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyObject DirectMapped]))


(def-pylib-fn PyTuple_Check
  "Return true if p is a tuple object or an instance of a subtype of the tuple type."
  Integer
  [p ensure-pyobj])


(defn PyTuple_New
  "Return value: New reference.

   Return a new tuple object of size len, or NULL on failure."
  ^Pointer [^long len]
  (DirectMapped/PyTuple_New (long len)))


(defn PyTuple_GetItem
  "Return value: Borrowed reference.

   Return the object at position pos in the tuple pointed to by p. If pos is out of
   bounds, return NULL and sets an IndexError exception."
  ^Pointer [p pos]
  (DirectMapped/PyTuple_GetItem (ensure-pyobj p) (long pos)))


(def-pylib-fn PyTuple_GetSlice
  "Return value: New reference.

   Take a slice of the tuple pointed to by p from low to high and return it as a new
   tuple."
  Pointer
  [p ensure-pyobj]
  [low jna/size-t]
  [high jna/size-t])


(defn PyTuple_SetItem
  "Insert a reference to object o at position pos of the tuple pointed to by p. Return 0
  on success.

   Note

   This function “steals” a reference to o"
  ^long [p pos o]
  (long (DirectMapped/PyTuple_SetItem (ensure-pyobj p)
                                      (long pos)
                                      (ensure-pyobj o))))
