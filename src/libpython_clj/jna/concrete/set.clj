(ns libpython-clj.jna.concrete.set
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


(def-pylib-fn PySet_Check
  "Return true if p is a set object or an instance of a subtype."
  Integer
  [p ensure-pyobj])


(def-pylib-fn PyFrozenSet_Check
  "Return true if p is a frozenset object or an instance of a subtype."
  Integer
  [p ensure-pyobj])


(def-pylib-fn PySet_New
  "Return value: New reference.

   Return a new set containing objects returned by the iterable. The iterable may be
   NULL to create a new empty set. Return the new set on success or NULL on
   failure. Raise TypeError if iterable is not actually iterable. The constructor is
   also useful for copying a set (c=set(s))."
  Pointer
  [iterable ensure-pyobj])


(def-pylib-fn PyFrozenSet_New
  "Return value: New reference.

   Return a new frozenset containing objects returned by the iterable. The iterable may
   be NULL to create a new empty frozenset. Return the new set on success or NULL on
   failure. Raise TypeError if iterable is not actually iterable."
  Pointer
  [iterable ensure-pyobj])


(def-pylib-fn PySet_Contains
  "Return 1 if found, 0 if not found, and -1 if an error is encountered. Unlike the
  Python __contains__() method, this function does not automatically convert unhashable
  sets into temporary frozensets. Raise a TypeError if the key is unhashable. Raise
  PyExc_SystemError if anyset is not a set, frozenset, or an instance of a subtype."
  Integer
  [anyset ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PySet_Add
  "Add key to a set instance. Also works with frozenset instances (like
  PyTuple_SetItem() it can be used to fill-in the values of brand new frozensets before
  they are exposed to other code). Return 0 on success or -1 on failure. Raise a
  TypeError if the key is unhashable. Raise a MemoryError if there is no room to
  grow. Raise a SystemError if set is not an instance of set or its subtype."
  Integer
  [set ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PySet_Discard
  "Return 1 if found and removed, 0 if not found (no action taken), and -1 if an error
  is encountered. Does not raise KeyError for missing keys. Raise a TypeError if the key
  is unhashable. Unlike the Python discard() method, this function does not
  automatically convert unhashable sets into temporary frozensets. Raise
  PyExc_SystemError if set is not an instance of set or its subtype."
  Integer
  [set ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PySet_Pop
  "Return value: New reference.

   Return a new reference to an arbitrary object in the set, and removes the object from
   the set. Return NULL on failure. Raise KeyError if the set is empty. Raise a
   SystemError if set is not an instance of set or its subtype."
  Pointer
  [set ensure-pyobj])


(def-pylib-fn PySet_Clear
  "Empty an existing set of all elements."
  Integer
  [set ensure-pyobj])
