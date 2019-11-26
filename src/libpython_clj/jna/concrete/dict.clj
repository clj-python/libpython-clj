(ns libpython-clj.jna.concrete.dict
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



(def-pylib-fn PyDict_Check
  "Return true if p is a dict object or an instance of a subtype of the dict type."
  Integer
  [p ensure-pyobj])


(def-pylib-fn PyDict_New
  "Return value: New reference.

   Return a new empty dictionary, or NULL on failure."
  Pointer)


(def-pylib-fn PyDictProxy_New
  "Return value: New reference.

   Return a types.MappingProxyType object for a mapping which enforces read-only
   behavior. This is normally used to create a view to prevent modification of the
   dictionary for non-dynamic class types."
  Pointer
  [mapping ensure-pyobj])


(def-pylib-fn PyDict_Clear
  "Empty an existing dictionary of all key-value pairs."
  nil
  [p ensure-pyobj])


(def-pylib-fn PyDict_Contains
  "Determine if dictionary p contains key. If an item in p is matches key, return 1,
  otherwise return 0. On error, return -1. This is equivalent to the Python expression
  key in p."
  Integer
  [p ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PyDict_Copy
  "Return value: New reference.

   Return a new dictionary that contains the same key-value pairs as p."
  Pointer
  [p ensure-pyobj])


(def-pylib-fn PyDict_SetItem
  "Insert value into the dictionary p with a key of key. key must be hashable; if it
  isn’t, TypeError will be raised. Return 0 on success or -1 on failure."
  Integer
  [p ensure-pyobj]
  [key ensure-pyobj]
  [val ensure-pyobj])


(def-pylib-fn PyDict_SetItemString
  "Insert value into the dictionary p using key as a key. key should be a const
  char*. The key object is created using PyUnicode_FromString(key). Return 0 on success
  or -1 on failure."
  Integer
  [p ensure-pyobj]
  [key ensure-pyobj]
  [val ensure-pyobj])


(def-pylib-fn PyDict_DelItem
  "Remove the entry in dictionary p with key key. key must be hashable; if it isn’t,
  TypeError is raised. Return 0 on success or -1 on failure."
  Integer
  [p ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PyDict_DelItemString
  "Remove the entry in dictionary p which has a key specified by the string key. Return
  0 on success or -1 on failure."
  Integer
  [p ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PyDict_GetItem
  "Return value: Borrowed reference.

   Return the object from dictionary p which has a key key. Return NULL if the key key
   is not present, but without setting an exception.

   Note that exceptions which occur while calling __hash__() and __eq__() methods will
   get suppressed. To get error reporting use PyDict_GetItemWithError() instead."
  Pointer
  [p ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PyDict_GetItemWithError
  "Return value: Borrowed reference.

   Variant of PyDict_GetItem() that does not suppress exceptions. Return NULL with an
   exception set if an exception occurred. Return NULL without an exception set if the
   key wasn’t present."
  Pointer
  [p ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PyDict_GetItemString
  "Return value: Borrowed reference.

   This is the same as PyDict_GetItem(), but key is specified as a const char*, rather
   than a PyObject*.

   Note that exceptions which occur while calling __hash__() and __eq__() methods and
   creating a temporary string object will get suppressed. To get error reporting use
   PyDict_GetItemWithError() instead."
  Pointer
  [p ensure-pyobj]
  [key ensure-pyobj])




(def-pylib-fn PyDict_SetDefault
  "Return value: Borrowed reference.

   This is the same as the Python-level dict.setdefault(). If present, it returns the
   value corresponding to key from the dictionary p. If the key is not in the dict, it
   is inserted with value defaultobj and defaultobj is returned. This function evaluates
   the hash function of key only once, instead of evaluating it independently for the
   lookup and the insertion.

    New in version 3.4."
  Pointer
  [p ensure-pyobj]
  [key ensure-pyobj]
  [defaultobj ensure-pyobj])


(def-pylib-fn PyDict_Items
  "Return value: New reference.

   Return a PyListObject containing all the items from the dictionary."
  Pointer
  [p ensure-pyobj])


(def-pylib-fn PyDict_Keys
  "Return value: New reference.

   Return a PyListObject containing all the keys from the dictionary."
  Pointer
  [p ensure-pyobj])


(def-pylib-fn PyDict_Values
  "Return value: New reference.

   Return a PyListObject containing all the values from the dictionary p."
  Pointer
  [p ensure-pyobj])


(def-pylib-fn PyDict_Size
  "Return the number of items in the dictionary. This is equivalent to len(p) on a
  dictionary."
  size-t-type
  [p ensure-pyobj])


(def-pylib-fn PyDict_Next
  "Iterate over all key-value pairs in the dictionary p. The Py_ssize_t referred to by
  ppos must be initialized to 0 prior to the first call to this function to start the
  iteration; the function returns true for each pair in the dictionary, and false once
  all pairs have been reported. The parameters pkey and pvalue should either point to
  PyObject* variables that will be filled in with each key and value, respectively, or
  may be NULL. Any references returned through them are borrowed. ppos should not be
  altered during iteration. Its value represents offsets within the internal dictionary
  structure, and since the structure is sparse, the offsets are not consecutive."
  Integer
  [p ensure-pyobj]
  [ppos (partial jna/ensure-type jna/size-t-ref-type)]
  [pkey jna/ensure-ptr-ptr]
  [pvalue jna/ensure-ptr-ptr])


(def-pylib-fn PyDict_Merge
  "Iterate over mapping object b adding key-value pairs to dictionary a. b may be a
  dictionary, or any object supporting PyMapping_Keys() and PyObject_GetItem(). If
  override is true, existing pairs in a will be replaced if a matching key is found in
  b, otherwise pairs will only be added if there is not a matching key in a. Return 0 on
  success or -1 if an exception was raised."
  Integer
  [a ensure-pyobj]
  [b ensure-pyobj]
  [override int])


(def-pylib-fn PyDict_Update
  "This is the same as PyDict_Merge(a, b, 1) in C, and is similar to a.update(b) in
  Python except that PyDict_Update() doesn’t fall back to the iterating over a sequence
  of key value pairs if the second argument has no “keys” attribute. Return 0 on success
  or -1 if an exception was raised."
  Integer
  [a ensure-pyobj]
  [b ensure-pyobj])


(def-pylib-fn PyDict_MergeFromSeq2
  "Update or merge into dictionary a, from the key-value pairs in seq2. seq2 must be an
  iterable object producing iterable objects of length 2, viewed as key-value pairs. In
  case of duplicate keys, the last wins if override is true, else the first wins. Return
  0 on success or -1 if an exception was raised. Equivalent Python (except for the
  return value):
def PyDict_MergeFromSeq2(a, seq2, override):
    for key, value in seq2:
        if override or key not in a:
            a[key] = value"
  Integer
  [a ensure-pyobj]
  [seq2 ensure-pyobj]
  [override int])
