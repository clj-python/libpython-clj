(ns libpython-clj.jna.protocols.sequence
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



(defn PySequence_Check
  "Return 1 if the object provides sequence protocol, and 0 otherwise. Note that it
  returns 1 for Python classes with a __getitem__() method unless they are dict
  subclasses since in general case it is impossible to determine what the type of keys
  it supports. This function always succeeds."
  ^long [o]
  (long (DirectMapped/PySequence_Check (ensure-pyobj o))))


(defn PySequence_Length
  "Returns the number of objects in sequence o on success, and -1 on failure. This is
  equivalent to the Python expression len(o)."
  ^long [o]
  (long (DirectMapped/PySequence_Length (ensure-pyobj o))))


(def-pylib-fn PySequence_Concat
  "Return value: New reference.

   Return the concatenation of o1 and o2 on success, and NULL on failure. This is the
   equivalent of the Python expression o1 + o2."
  Pointer
  [o1 ensure-pyobj]
  [o2 ensure-pyobj])


(def-pylib-fn PySequence_Repeat
  "Return value: New reference.

   Return the result of repeating sequence object o count times, or NULL on
   failure. This is the equivalent of the Python expression o * count."
  Pointer
  [o ensure-pyobj]
  [count jna/size-t])


(def-pylib-fn PySequence_InPlaceConcat
  "Return value: New reference.

   Return the concatenation of o1 and o2 on success, and NULL on failure. The operation
   is done in-place when o1 supports it. This is the equivalent of the Python expression
   o1 += o2."
  Pointer
  [o1 ensure-pyobj]
  [o2 ensure-pyobj])


(def-pylib-fn PySequence_InPlaceRepeat
  "Return value: New reference.

   Return the result of repeating sequence object o count times, or NULL on failure. The
   operation is done in-place when o supports it. This is the equivalent of the Python
   expression o *= count."
  Pointer
  [o ensure-pyobj]
  [count jna/size-t])


(defn PySequence_GetItem
  "Return value: New reference.

   Return the ith element of o, or NULL on failure. This is the equivalent of the Python
   expression o[i]."
  ^Pointer [o i]
  (DirectMapped/PySequence_GetItem (ensure-pyobj o) (jna/size-t i)))


(def-pylib-fn PySequence_GetSlice
  "Return value: New reference.

   Return the slice of sequence object o between i1 and i2, or NULL on failure. This is
   the equivalent of the Python expression o[i1:i2]."
  Pointer
  [o ensure-pyobj]
  [i1 jna/size-t]
  [i2 jna/size-t])


(def-pylib-fn PySequence_SetItem
  "Assign object v to the ith element of o. Raise an exception and return -1 on failure;
   return 0 on success. This is the equivalent of the Python statement o[i] = v. This
   function does not steal a reference to v.

   If v is NULL, the element is deleted, however this feature is deprecated in favour
   of using PySequence_DelItem()."
  Integer
  [o ensure-pyobj]
  [i jna/size-t]
  [v ensure-pyobj])


(def-pylib-fn PySequence_DelItem
  "Delete the ith element of object o. Returns -1 on failure. This is the equivalent of
  the Python statement del o[i]."
  Integer
  [o ensure-pyobj]
  [i jna/size-t])


(def-pylib-fn PySequence_SetSlice
  "Assign the sequence object v to the slice in sequence object o from i1 to i2. This is
  the equivalent of the Python statement o[i1:i2] = v."
  Integer
  [o ensure-pyobj]
  [i1 jna/size-t]
  [i2 jna/size-t]
  [v ensure-pyobj])


(def-pylib-fn PySequence_DelSlice
  "Delete the slice in sequence object o from i1 to i2. Returns -1 on failure. This is
  the equivalent of the Python statement del o[i1:i2]."
  Integer
  [o ensure-pyobj]
  [i1 jna/size-t]
  [i2 jna/size-t])


(def-pylib-fn PySequence_Count
  "Return the number of occurrences of value in o, that is, return the number of keys
  for which o[key] == value. On failure, return -1. This is equivalent to the Python
  expression o.count(value)."
  size-t-type
  [o ensure-pyobj]
  [value ensure-pyobj])


(def-pylib-fn PySequence_Contains
  "Determine if o contains value. If an item in o is equal to value, return 1, otherwise
  return 0. On error, return -1. This is equivalent to the Python expression value in
  o."
  Integer
  [o ensure-pyobj]
  [value ensure-pyobj])


(def-pylib-fn PySequence_Index
  "Return the first index i for which o[i] == value. On error, return -1. This is
  equivalent to the Python expression o.index(value)."
  size-t-type
  [o ensure-pyobj]
  [value ensure-pyobj])


(def-pylib-fn PySequence_List
  "Return value: New reference.

   Return a list object with the same contents as the sequence or iterable o, or NULL on
   failure. The returned list is guaranteed to be new. This is equivalent to the Python
   expression list(o)."
  Pointer
  [o ensure-pyobj])


(def-pylib-fn PySequence_Tuple
  "Return value: New reference.

   Return a tuple object with the same contents as the sequence or iterable o, or NULL
   on failure. If o is a tuple, a new reference will be returned, otherwise a tuple will
   be constructed with the appropriate contents. This is equivalent to the Python
   expression tuple(o)."
  Pointer
  [o ensure-pyobj])
