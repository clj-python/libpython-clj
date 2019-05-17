(ns libpython-clj.jna
  (:require [tech.v2.datatype :as dtype]
            [tech.jna :as jna]
            [tech.jna.base :as jna-base]
            [tech.v2.datatype.typecast :as typecast])
  (:import [com.sun.jna Pointer NativeLibrary]))


(def ^:dynamic *python-library* "python3.7m")


(defprotocol PToPyObjectPtr
  (->py-object-ptr [item]))


(extend-type Object
  PToPyObjectPtr
  (->py-object-ptr [item]
    (jna/as-ptr item)))


(defn ensure-pyobj
  [item]
  (if-let [retval (->py-object-ptr item)]
    retval
    (throw (ex-info "Failed to get a pyobject pointer from object." {}))))



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
  [obj ensure-pyobj])


;; System Functionality


(def-pylib-fn PySys_SetArgv
  "This function works like PySys_SetArgvEx() with updatepath set to 1 unless the python
  interpreter was started with the -I.

    Use Py_DecodeLocale() to decode a bytes string to get a wchar_* string.

    Changed in version 3.4: The updatepath value depends on -I."
  nil
  [argc int]
  [argv ensure-pyobj])


;; Objects

(def-pylib-fn Py_DecRef
  "Decrement the refference count on an object"
  nil
  [py-obj ensure-pyobj])


(def-pylib-fn Py_IncRef
  "Increment the reference count on an object"
  nil
  [py-obj ensure-pyobj])


;;There is a good reason to use this function; then you aren't dependent upon the
;;compile time representation of the pyobject item itself.
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


(def-pylib-fn PyObject_Length
  "Return the length of object o. If the object o provides either the sequence and
  mapping protocols, the sequence length is returned. On error, -1 is returned. This is
  the equivalent to the Python expression len(o)."
  Integer
  [py-obj ensure-pyobj])


(defn find-pylib-symbol
  [sym-name]
  (.getGlobalVariableAddress ^NativeLibrary (jna-base/load-library *python-library*)
                             sym-name))


(defn Py_None
  []
  (find-pylib-symbol "_Py_NoneStruct"))


(defn Py_True
  []
  (find-pylib-symbol "_Py_TrueStruct"))


(defn Py_False
  []
  (find-pylib-symbol "_Py_FalseStruct"))


(def-pylib-fn PyBool_Check
  "Return true if o is of type PyBool_Type."
  Integer
  [py-obj ensure-pyobj])


(def-pylib-fn PyBool_FromLong
  "Return value: New reference.
    Return a new reference to Py_True or Py_False depending on the truth value of v."
  Pointer
  [v long])


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


(def-pylib-fn PyUnicode_AsUTF8AndSize
  "Return a pointer to the UTF-8 encoding of the Unicode object, and store the size of
   the encoded representation (in bytes) in size. The size argument can be NULL; in this
   case no size will be stored. The returned buffer always has an extra null byte
   appended (not included in size), regardless of whether there are any other null code
   points.

   In the case of an error, NULL is returned with an exception set and no size is stored.

   This caches the UTF-8 representation of the string in the Unicode object, and
   subsequent calls will return a pointer to the same buffer. The caller is not
   responsible for deallocating the buffer.

   New in version 3.3.

   Changed in version 3.7: The return type is now const char * rather of char *."
  Pointer
  [py-obj ensure-pyobj]
  [size-ptr jna/as-ptr])


(def-pylib-fn PyUnicode_AsUTF8
  "As PyUnicode_AsUTF8AndSize(), but does not store the size.

   New in version 3.3.

   Changed in version 3.7: The return type is now const char * rather of char *."
  Pointer
  [py-obj ensure-pyobj])


(def-pylib-fn PyMem_Free
  "Frees the memory block pointed to by p, which must have been returned by a previous
  call to PyMem_Malloc(), PyMem_Realloc() or PyMem_Calloc(). Otherwise, or if
  PyMem_Free(p) has been called before, undefined behavior occurs.

   If p is NULL, no operation is performed."
  nil
  [data-ptr jna/as-ptr])
