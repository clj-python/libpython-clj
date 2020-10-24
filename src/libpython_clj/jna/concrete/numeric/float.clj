(ns libpython-clj.jna.concrete.numeric.float
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     *python-library*]
             :as libpy-base])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyObject DirectMapped]))


(def-pylib-fn PyFloat_Check
  "Return true if its argument is a PyFloatObject or a subtype of PyFloatObject."
  Integer
  [p ensure-pyobj])


(def-pylib-fn PyFloat_CheckExact
  "Return true if its argument is a PyFloatObject, but not a subtype of PyFloatObject."
  Integer
  [p ensure-pyobj])


(def-pylib-fn PyFloat_FromString
  "Return value: New reference.

   Create a PyFloatObject object based on the string value in str, or NULL on failure."
  Pointer
  [str ensure-pyobj])


(defn PyFloat_FromDouble
  "Return value: New reference.

   Create a PyFloatObject object from v, or NULL on failure."
  ^Pointer [v]
  (DirectMapped/PyFloat_FromDouble (double v)))


(defn PyFloat_AsDouble
  "Return a C double representation of the contents of pyfloat. If pyfloat is not a
  Python floating point object but has a __float__() method, this method will first be
  called to convert pyfloat into a float. This method returns -1.0 upon failure, so one
  should call PyErr_Occurred() to check for errors."
  ^double [v]
  (DirectMapped/PyFloat_AsDouble (ensure-pyobj v)))


(def-pylib-fn PyFloat_GetInfo
  "Return value: New reference.

   Return a structseq instance which contains information about the precision, minimum
   and maximum values of a float. It’s a thin wrapper around the header file float.h."
  Pointer)


(def-pylib-fn PyFloat_GetMax
  "Return the maximum representable finite float DBL_MAX as C double."
  Double)


(def-pylib-fn PyFloat_GetMin
  "Return the minimum normalized positive float DBL_MIN as C double."
  Double)
