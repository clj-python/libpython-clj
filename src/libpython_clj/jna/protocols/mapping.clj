(ns libpython-clj.jna.protocols.mapping
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



(def-pylib-fn PyMapping_Check
  "Return 1 if the object provides mapping protocol or supports slicing, and 0
  otherwise. Note that it returns 1 for Python classes with a __getitem__() method since
  in general case it is impossible to determine what the type of keys it supports. This
  function always succeeds."
  Integer
  [o ensure-pyobj])


(def-pylib-fn PyMapping_Length
  "Returns the number of keys in object o on success, and -1 on failure. This is
  equivalent to the Python expression len(o)."
  size-t-type
  [o ensure-pyobj])


(def-pylib-fn PyMapping_GetItemString
  "Return value: New reference.

   Return element of o corresponding to the string key or NULL on failure. This is the
   equivalent of the Python expression o[key]. See also PyObject_GetItem()."
  PyObject
  [o ensure-pyobj]
  [key str])


(def-pylib-fn PyMapping_SetItemString
  "Map the string key to the value v in object o. Returns -1 on failure. This is the
  equivalent of the Python statement o[key] = v. See also PyObject_SetItem()."
  Integer
  [o ensure-pyobj]
  [key str]
  [v ensure-pyobj])


(def-pylib-fn PyMapping_DelItem
  "Remove the mapping for the object key from the object o. Return -1 on failure. This
  is equivalent to the Python statement del o[key]. This is an alias of
  PyObject_DelItem()."
  Integer
  [o ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PyMapping_DelItemString
  "Remove the mapping for the string key from the object o. Return -1 on failure. This
  is equivalent to the Python statement del o[key]."
  Integer
  [o ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PyMapping_HasKey
  "Return 1 if the mapping object has the key key and 0 otherwise. This is equivalent to
  the Python expression key in o. This function always succeeds.

   Note that exceptions which occur while calling the __getitem__() method will get
   suppressed. To get error reporting use PyObject_GetItem() instead."
  Integer
  [o ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PyMapping_HasKeyString
  "Return 1 if the mapping object has the key key and 0 otherwise. This is equivalent to
  the Python expression key in o. This function always succeeds.

   Note that exceptions which occur while calling the __getitem__() method and creating
   a temporary string object will get suppressed. To get error reporting use
   PyMapping_GetItemString() instead."
  Integer
  [o ensure-pyobj]
  [key str])


(def-pylib-fn PyMapping_Keys
  "Return value: New reference.

   On success, return a list of the keys in object o. On failure, return NULL.

   Changed in version 3.7: Previously, the function returned a list or a tuple."
  PyObject
  [o ensure-pyobj])


(def-pylib-fn PyMapping_Values
  "Return value: New reference.

   On success, return a list of the values in object o. On failure, return NULL.

   Changed in version 3.7: Previously, the function returned a list or a tuple."
  PyObject
  [o ensure-pyobj])


(def-pylib-fn PyMapping_Items
  "Return value: New reference.

   On success, return a list of the items in object o, where each item is a tuple
   containing a key-value pair. On failure, return NULL.

   Changed in version 3.7: Previously, the function returned a list or a tuple."
  PyObject
  [o ensure-pyobj])
