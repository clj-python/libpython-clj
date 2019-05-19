(ns libpython-clj.jna.protocols.object
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.jna.base :as jna-base]
            [tech.jna :as jna]
            [camel-snake-kebab.core :refer [->kebab-case]])
  (:import [com.sun.jna Pointer Native NativeLibrary]
           [com.sun.jna.ptr PointerByReference]))


;; Object Protocol

(def-pylib-fn Py_DecRef
  "Decrement the refference count on an object"
  nil
  [py-obj ensure-pyobj])


(def-pylib-fn Py_IncRef
  "Increment the reference count on an object"
  nil
  [py-obj ensure-pyobj])


;; object.h:937
(def bool-fn-table
  (->> {"Py_LT" 0
        "Py_LE" 1
        "Py_EQ" 2
        "Py_NE" 3
        "Py_GT" 4
        "Py_GE" 5}
       (map (fn [[k v]]
              [(-> (->kebab-case k)
                   keyword)
               v]))
       (into {})))


(defn bool-fn-constant
  [item]
  (let [value (cond
                (number? item)
                (long item)
                (keyword? item)
                (get bool-fn-table item))
        value-set (set (vals bool-fn-table))]
    (when-not (contains? value-set value)
      (throw (ex-info (format "Unrecognized bool fn %s" item) {})))
    (int value)))


;;There is a good reason to use this function; then you aren't dependent upon the
;;compile time representation of the pyobject item itself which changes if, for instance,
;;tracing is enabled.
(def-pylib-fn PyObject_Type
  "Return value: New reference.

   When o is non-NULL, returns a type object corresponding to the object type of object
   o. On failure, raises SystemError and returns NULL. This is equivalent to the Python
   expression type(o). This function increments the reference count of the return
   value. There’s really no reason to use this function instead of the common
   expression o->ob_type, which returns a pointer of type PyTypeObject*, except when
   the incremented reference count is needed."
  Pointer
  [py-obj ensure-pyobj])



(def-pylib-fn PyObject_Repr
  "Return value: New reference.

   Compute a string representation of object o. Returns the string representation on
   success, NULL on failure. This is the equivalent of the Python expression
   repr(o). Called by the repr() built-in function.

   Changed in version 3.4: This function now includes a debug assertion to help ensure
   that it does not silently discard an active exception."
  Pointer
  [py_obj ensure-pyobj])


(def-pylib-fn PyObject_Str
  "Return value: New reference.

   Compute a string representation of object o. Returns the string representation on
   success, NULL on failure. This is the equivalent of the Python expression str(o). Called
   by the str() built-in function and by the print statement."
  Pointer
  [py-obj ensure-pyobj])


(def-pylib-fn PyObject_HasAttr
  "Returns 1 if o has the attribute attr_name, and 0 otherwise. This is equivalent to
  the Python expression hasattr(o, attr_name). This function always succeeds.

   Note that exceptions which occur while calling __getattr__() and __getattribute__()
   methods will get suppressed. To get error reporting use PyObject_GetAttr() instead."
  Integer
  [pyobj ensure-pyobj]
  [attr-name ensure-pyobj])


(def-pylib-fn PyObject_HasAttrString
  "Returns 1 if o has the attribute attr_name, and 0 otherwise. This is equivalent to
  the Python expression hasattr(o, attr_name). This function always succeeds.

   Note that exceptions which occur while calling __getattr__() and __getattribute__()
   methods and creating a temporary string object will get suppressed. To get error
   reporting use PyObject_GetAttrString() instead."
  Integer
  [pyobj ensure-pyobj]
  [attr-name str])


(def-pylib-fn PyObject_GetAttr
  "Return value: New reference.

   Retrieve an attribute named attr_name from object o. Returns the attribute value on
   success, or NULL on failure. This is the equivalent of the Python expression
   o.attr_name."
  Pointer
  [pyobj ensure-pyobj]
  [attr-name ensure-pyobj])


(def-pylib-fn PyObject_GetAttrString
  "Return value: New reference.

   Retrieve an attribute named attr_name from object o. Returns the attribute value on
   success, or NULL on failure. This is the equivalent of the Python expression
   o.attr_name."
  Pointer
  [pyobj ensure-pyobj]
  [attr-name str])


(def-pylib-fn PyObject_GenericGetAttr
  "Return value: New reference.

   Generic attribute getter function that is meant to be put into a type object’s
   tp_getattro slot. It looks for a descriptor in the dictionary of classes in the
   object’s MRO as well as an attribute in the object’s __dict__ (if present). As
   outlined in Implementing Descriptors, data descriptors take preference over instance
   attributes, while non-data descriptors don’t. Otherwise, an AttributeError is
   raised."
  Pointer
  [pyobj ensure-pyobj]
  [attr-name ensure-pyobj])


(def-pylib-fn PyObject_SetAttr
  "Set the value of the attribute named attr_name, for object o, to the value v. Raise
   an exception and return -1 on failure; return 0 on success. This is the equivalent of
   the Python statement o.attr_name = v.

   If v is NULL, the attribute is deleted, however this feature is deprecated in favour
   of using PyObject_DelAttr()."
  Integer
  [pyobj ensure-pyobj]
  [attr-name ensure-pyobj]
  [v ensure-pyobj])



(def-pylib-fn PyObject_SetAttrString
  "Set the value of the attribute named attr_name, for object o, to the value v. Raise
   an exception and return -1 on failure; return 0 on success. This is the equivalent of
   the Python statement o.attr_name = v.

   If v is NULL, the attribute is deleted, however this feature is deprecated in favour
   of using PyObject_DelAttrString()."
  Integer
  [pyobj ensure-pyobj]
  [attr-name str]
  [v ensure-pyobj])


(def-pylib-fn PyObject_GenericSetAttr
  "Generic attribute setter and deleter function that is meant to be put into a type
  object’s tp_setattro slot. It looks for a data descriptor in the dictionary of classes
  in the object’s MRO, and if found it takes preference over setting or deleting the
  attribute in the instance dictionary. Otherwise, the attribute is set or deleted in
  the object’s __dict__ (if present). On success, 0 is returned, otherwise an
  AttributeError is raised and -1 is returned."
  Integer
  [pyobj ensure-pyobj]
  [attr-name ensure-pyobj]
  [v ensure-pyobj])


(def-pylib-fn PyObject_DelAttr
  "Delete attribute named attr_name, for object o. Returns -1 on failure. This is the
  equivalent of the Python statement del o.attr_name."
  Integer
  [pyobj ensure-pyobj]
  [attr-name ensure-pyobj])


(def-pylib-fn PyObject_DelAttrString
  "Delete attribute named attr_name, for object o. Returns -1 on failure. This is the
  equivalent of the Python statement del o.attr_name."
  Integer
  [pyobj ensure-pyobj]
  [attr-name str])


(def-pylib-fn PyObject_GenericGetDict
  "Return value: New reference.

   A generic implementation for the getter of a __dict__ descriptor. It creates the
   dictionary if necessary.

   New in version 3.3."
  Pointer
  [pyobj ensure-pyobj]
  [context ensure-pyobj])


(def-pylib-fn PyObject_GenericSetDict
  "A generic implementation for the setter of a __dict__ descriptor. This implementation
   does not allow the dictionary to be deleted.

   New in version 3.3."
  Integer
  [pyobj ensure-pyobj]
  [context ensure-pyobj])


(def-pylib-fn PyObject_RichCompare
  "Return value: New reference.

   Compare the values of o1 and o2 using the operation specified by opid, which must be
   one of Py_LT, Py_LE, Py_EQ, Py_NE, Py_GT, or Py_GE, corresponding to <, <=, ==, !=,
   >, or >= respectively. This is the equivalent of the Python expression o1 op o2,
   where op is the operator corresponding to opid. Returns the value of the comparison
   on success, or NULL on failure."
  Pointer
  [o1 ensure-pyobj]
  [o2 ensure-pyobj]
  [opid bool-fn-constant])


(def-pylib-fn PyObject_RichCompareBool
  "Compare the values of o1 and o2 using the operation specified by opid, which must be
  one of Py_LT, Py_LE, Py_EQ, Py_NE, Py_GT, or Py_GE, corresponding to <, <=, ==, !=, >,
  or >= respectively. Returns -1 on error, 0 if the result is false, 1 otherwise. This
  is the equivalent of the Python expression o1 op o2, where op is the operator
  corresponding to opid.

   Note

   If o1 and o2 are the same object, PyObject_RichCompareBool() will always return 1 for
   Py_EQ and 0 for Py_NE."
  Integer
  [o1 ensure-pyobj]
  [o2 ensure-pyobj]
  [opid bool-fn-constant])


(def-pylib-fn PyCallable_Check
  "Determine if the object o is callable. Return 1 if the object is callable and 0
  otherwise. This function always succeeds."
  Integer
  [pyobj ensure-pyobj])


(def-pylib-fn PyObject_Call
  "Return value: New reference.

   Call a callable Python object callable, with arguments given by the tuple args, and
   named arguments given by the dictionary kwargs.

   args must not be NULL, use an empty tuple if no arguments are needed. If no named
   arguments are needed, kwargs can be NULL.

   Returns the result of the call on success, or NULL on failure.

   This is the equivalent of the Python expression: callable(*args, **kwargs)."
  Pointer
  [callable ensure-pyobj]
  [args ensure-pytuple]
  [kwargs jna/as-ptr])

(def-pylib-fn PyObject_CallObject
  "Return value: New reference.

   Call a callable Python object callable, with arguments given by the tuple args. If no
   arguments are needed, then args can be NULL.

   Returns the result of the call on success, or NULL on failure.

   This is the equivalent of the Python expression: callable(*args)."
  Pointer
  [callable ensure-pyobj]
  [args jna/as-ptr])


(def-pylib-fn PyObject_Hash
  "Compute and return the hash value of an object o. On failure, return -1. This is the
  equivalent of the Python expression hash(o).

   Changed in version 3.2: The return type is now Py_hash_t. This is a signed integer
   the same size as Py_ssize_t."
  (size-t-type)
  [o ensure-pyobj])


(def-pylib-fn PyObject_IsTrue
  "Returns 1 if the object o is considered to be true, and 0 otherwise. This is
  equivalent to the Python expression not not o. On failure, return -1."
  Integer
  [py-obj ensure-pyobj])


(def-pylib-fn PyObject_Not
  "Returns 0 if the object o is considered to be true, and 1 otherwise. This is
  equivalent to the Python expression not o. On failure, return -1."
  Integer
  [py-obj ensure-pyobj])


(def-pylib-fn PyObject_Length
  "Return the length of object o. If the object o provides either the sequence and
  mapping protocols, the sequence length is returned. On error, -1 is returned. This is
  the equivalent to the Python expression len(o)."
  Integer
  [py-obj ensure-pyobj])


(def-pylib-fn PyObject_GetItem
  "Return value: New reference.

   Return element of o corresponding to the object key or NULL on failure. This is the
   equivalent of the Python expression o[key]."
  Pointer
  [o ensure-pyobj]
  [key ensure-pyobj])


(def-pylib-fn PyObject_SetItem
  "Map the object key to the value v. Raise an exception and return -1 on failure;
   return 0 on success. This is the equivalent of the Python statement o[key] = v."
  Integer
  [o ensure-pyobj]
  [key ensure-pyobj]
  [v ensure-pyobj])


(def-pylib-fn PyObject_DelItem
  "Remove the mapping for the object key from the object o. Return -1 on failure. This
  is equivalent to the Python statement del o[key]."
  Integer
  [o ensure-pyobj]
  [key ensure-pyobj])



(def-pylib-fn PyObject_Dir
  "Return value: New reference.

   This is equivalent to the Python expression dir(o), returning a (possibly empty)
   list of strings appropriate for the object argument, or NULL if there was an
   error. If the argument is NULL, this is like the Python dir(), returning the names
   of the current locals; in this case, if no execution frame is active then NULL is
   returned but PyErr_Occurred() will return false."
  Pointer
  [o ensure-pyobj])


(def-pylib-fn PyObject_GetIter
  "Return value: New reference.

   This is equivalent to the Python expression iter(o). It returns a new iterator for
   the object argument, or the object itself if the object is already an
   iterator. Raises TypeError and returns NULL if the object cannot be iterated."
  Pointer
  [o ensure-pyobj])
