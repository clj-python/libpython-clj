(ns libpython-clj.jna
  (:require [tech.v2.datatype :as dtype]
            [tech.jna :as jna]
            [tech.v2.datatype.typecast :as typecast])
  (:import [com.sun.jna Pointer]))


(def ^:dynamic *python-library* "python3.7m")


(defmacro def-pylib-fn
  [fn-name docstring & args]
  `(jna/def-jna-fn *python-library* ~fn-name ~docstring ~@args))


;; Bugs and caveats: The destruction of modules and objects in modules is done in random
;; order; this may cause destructors (__del__() methods) to fail when they depend on
;; other objects (even functions) or modules. Dynamically loaded extension modules
;; loaded by Python are not unloaded. Small amounts of memory allocated by the Python
;; interpreter may not be freed (if you find a leak, please report it). Memory tied up
;; in circular references between objects is not freed. Some memory allocated by
;; extension modules may not be freed. Some extensions may not work properly if their
;; initialization routine is called more than once; this can happen if an application
;; calls Py_Initialize() and Py_Finalize() more than once.


(def-pylib-fn Py_InitializeEx
  "This function works like Py_Initialize() if initsigs is 1. If initsigs is 0, it skips
  initialization registration of signal handlers, which might be useful when Python is
  embedded."
  nil
  [initsigs int])



(def-pylib-fn Py_IsInitialized
  "Return true (nonzero) when the Python interpreter has been initialized, false (zero)
  if not. After Py_Finalize() is called, this returns false until Py_Initialize() is
  called again."
  Integer)


(def-pylib-fn Py_FinalizeEx
  "Undo all initializations made by Py_Initialize() and subsequent use of Python/C API
  functions, and destroy all sub-interpreters (see Py_NewInterpreter() below) that were
  created and not yet destroyed since the last call to Py_Initialize(). Ideally, this
  frees all memory allocated by the Python interpreter. This is a no-op when called for
  a second time (without calling Py_Initialize() again first). There is no return value;
  errors during finalization are ignored."
  Integer)


(def-pylib-fn PyRun_SimpleString
  "This is a simplified interface to PyRun_SimpleStringFlags() below, leaving the
  PyCompilerFlags* argument set to NULL."
  Integer
  [command str])


;; Errors


(def-pylib-fn PyErr_Clear
  "Clear the error indicator. If the error indicator is not set, there is no effect."
  nil)


(def-pylib-fn PyErr_Occurred
  "Return value: Borrowed reference.

    Test whether the error indicator is set. If set, return the exception type (the
    first argument to the last call to one of the PyErr_Set*() functions or to
    PyErr_Restore()). If not set, return NULL. You do not own a reference to the return
    value, so you do not need to Py_DECREF() it.

    Note

    Do not compare the return value to a specific exception; use
    PyErr_ExceptionMatches() instead, shown below. (The comparison could easily fail
    since the exception may be an instance instead of a class, in the case of a class
    exception, or it may be a subclass of the expected exception.)"
  Pointer)





(def-pylib-fn PyErr_PrintEx
  "Call this function only when the error indicator is set. Otherwise it will cause a
  fatal error!

  If set_sys_last_vars is nonzero, the variables sys.last_type, sys.last_value and
  sys.last_traceback will be set to the type, value and traceback of the printed
  exception, respectively.

  Print a standard traceback to sys.stderr and clear the error indicator. Unless the
  error is a SystemExit. In that case the no traceback is printed and Python process
  will exit with the error code specified by the SystemExit instance."
  nil
  [set_sys_last_vars int])





(def-pylib-fn PyErr_Print
  "Alias for PyErr_PrintEx(1)."
  nil)



(def-pylib-fn PyErr_WriteUnraisable
  "This utility function prints a warning message to sys.stderr when an exception has
  been set but it is impossible for the interpreter to actually raise the exception. It
  is used, for example, when an exception occurs in an __del__() method.

  The function is called with a single argument obj that identifies the context in which
  the unraisable exception occurred. If possible, the repr of obj will be printed in the
  warning message."
  nil
  [obj jna/as-ptr])
