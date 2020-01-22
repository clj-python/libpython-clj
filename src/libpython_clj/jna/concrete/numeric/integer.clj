(ns libpython-clj.jna.concrete.numeric.integer
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     *python-library*]
             :as libpy-base]
            [tech.jna :as jna])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyObject DirectMapped]))


(def-pylib-fn PyLong_Check
  "Return true if its argument is a PyLongObject or a subtype of PyLongObject."
  Integer
  [p ensure-pyobj])



(def-pylib-fn PyLong_CheckExact
  "Return true if its argument is a PyLongObject, but not a subtype of PyLongObject."
  Integer
  [p ensure-pyobj])


(def-pylib-fn PyLong_FromLong
  "Return value: New reference.

   Return a new PyLongObject object from v, or NULL on failure.

   The current implementation keeps an array of integer objects for all integers between
   -5 and 256, when you create an int in that range you actually just get back a
   reference to the existing object. So it should be possible to change the value of
   1. I suspect the behaviour of Python in this case is undefined. :-)"
  Pointer
  [v int])


(def-pylib-fn PyLong_FromUnsignedLong
  "Return value: New reference.

   Return a new PyLongObject object from a C unsigned long, or NULL on failure."
  Pointer
  [v unchecked-int])


(def-pylib-fn PyLong_FromSsize_t
  "Return value: New reference.

   Return a new PyLongObject object from a C Py_ssize_t, or NULL on failure."
  Pointer
  [v jna/size-t])


(defn PyLong_FromLongLong
  "Return value: New reference.

   Return a new PyLongObject object from a C long long, or NULL on failure."
  ^Pointer [v]
  (DirectMapped/PyLong_FromLongLong (long v)))


(def-pylib-fn PyLong_FromUnsignedLongLong
  "Return value: New reference.

   Return a new PyLongObject object from a C unsigned long long, or NULL on failure."
  Pointer
  [v unchecked-long])


(def-pylib-fn PyLong_FromDouble
  "Return value: New reference.

   Return a new PyLongObject object from the integer part of v, or NULL on failure."
  Pointer
  [v double])


(def-pylib-fn PyLong_AsLong
  "Return a C long representation of obj. If obj is not an instance of PyLongObject,
  first call its __int__() method (if present) to convert it to a PyLongObject.

   Raise OverflowError if the value of obj is out of range for a long.

   Returns -1 on error. Use PyErr_Occurred() to disambiguate."
  Integer
  [obj ensure-pyobj])


(defn PyLong_AsLongLong
  "Return a C long long representation of obj. If obj is not an instance of
  PyLongObject, first call its __int__() method (if present) to convert it to a
  PyLongObject.

   Raise OverflowError if the value of obj is out of range for a long.

   Returns -1 on error. Use PyErr_Occurred() to disambiguate."
  ^long [obj]
  (DirectMapped/PyLong_AsLongLong (ensure-pyobj obj)))
