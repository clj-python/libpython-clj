(ns libpython-clj.jna.concrete.cfunction
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     as-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.jna :as jna])
  (:import [com.sun.jna CallbackReference Pointer Callback]
           [libpython_clj.jna
            CFunction$KeyWordFunction
            CFunction$TupleFunction
            PyMethodDef
            PyObject]))


;; #define METH_OLDARGS  0x0000   -- unsupported now
(def METH_VARARGS  0x0001)
(def METH_KEYWORDS 0x0002)

;; METH_NOARGS and METH_O must not be combined with the flags above.
(def METH_NOARGS   0x0004)
(def METH_O        0x0008)

;; /* METH_CLASS and METH_STATIC are a little different; these control
;;    the construction of methods for a class.  These cannot be used for
;;    functions in modules. */
(def METH_CLASS    0x0010)
(def METH_STATIC   0x0020)

;; /* METH_COEXIST allows a method to be entered even though a slot has
;;    already filled the entry.  When defined, the flag allows a separate
;;    method, "__contains__" for example, to coexist with a defined
;;    slot like sq_contains. */

(def METH_COEXIST   0x0040)

;; We aren't going there for either of these...
;; #ifndef Py_LIMITED_API
;; #define METH_FASTCALL  0x0080
;; #endif

;; /* This bit is preserved for Stackless Python */
;; #ifdef STACKLESS
;; #define METH_STACKLESS 0x0100
;; #else
;; #define METH_STACKLESS 0x0000
;; #endif


(def-pylib-fn PyCFunction_NewEx
  "Create a new callable from an item."
  Pointer
  [method-def (partial jna/ensure-type PyMethodDef)]
  [self as-pyobj]
  [module as-pyobj])


(def-pylib-fn PyInstanceMethod_New
  "Return value: New reference.

  Return a new instance method object, with func being any callable object func is the
  function that will be called when the instance method is called."
  Pointer
  [func ensure-pyobj])


(def-pylib-fn PyInstanceMethod_Function
  "Return value: Borrowed reference.

  Return the function object associated with the instance method im."
  Pointer
  [im ensure-pyobj])
