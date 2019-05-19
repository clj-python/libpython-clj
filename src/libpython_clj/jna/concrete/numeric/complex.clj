(ns libpython-clj.jna.conrete.numeric.complex
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     find-pylib-symbol]
             :as libpy-base]
            [tech.jna :as jna])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyComplex PyComplex$ByValue]))


(def-pylib-fn PyComplex_Check
  "Return true if its argument is a PyComplexObject or a subtype of PyComplexObject."
  Integer
  [p ensure-pyobj])


(def-pylib-fn PyComplex_FromCComplex
  "Return value: New reference.

   Create a new Python complex number object from a C Py_complex value."
  Pointer
  [v (partial jna/ensure-type PyComplex$ByValue)])


(def-pylib-fn PyComplex_FromDoubles
  "Return value: New reference.

   Return a new PyComplexObject object from real and imag."
  Pointer
  [real double]
  [imag double])


(def-pylib-fn PyComplex_RealAsDouble
  "Return the real part of op as a C double."
  Double
  [op ensure-pyobj])


(def-pylib-fn PyComplex_ImagAsDouble
  "Return the imaginary part of op as a C double."
  Double
  [op ensure-pyobj])


(def-pylib-fn PyComplex_AsCComplex
  "Return the Py_complex value of the complex number op.

   If op is not a Python complex number object but has a __complex__() method, this
   method will first be called to convert op to a Python complex number object. Upon
   failure, this method returns -1.0 as a real value."
  PyComplex$ByValue
  [op ensure-pyobj])
