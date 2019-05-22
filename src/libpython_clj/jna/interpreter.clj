(ns libpython-clj.jna.interpreter
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     find-pylib-symbol
                     *python-library*]
             :as libpy-base]
            [tech.jna.base :as jna-base]
            [tech.jna :as jna]
            [libpython-clj.jna.protocols.object :as pyobj]
            [libpython-clj.jna.concrete.unicode :as pyuni]
            [camel-snake-kebab.core :refer [->kebab-case]])
  (:import [com.sun.jna Pointer Native NativeLibrary]
           [com.sun.jna.ptr PointerByReference]
           [libpython_clj.jna PyObject]))


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
  PyObject)


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


;; chrisn@chrisn-dt:~/dev/cnuernber/libpython-clj$ nm -D /usr/lib/x86_64-linux-gnu/libpython3.7m.so | grep -i _type
;; Then a few more transformations.
(def type-symbol-names
  ["PyAsyncGen_Type"
   "PyBaseObject_Type"
   "PyBool_Type"
   "PyBufferedIOBase_Type"
   "PyBufferedRandom_Type"
   "PyBufferedReader_Type"
   "PyBufferedRWPair_Type"
   "PyBufferedWriter_Type"
   "PyByteArrayIter_Type"
   "PyByteArray_Type"
   "PyBytesIO_Type"
   "PyBytesIter_Type"
   "PyBytes_Type"
   "PyCallIter_Type"
   "PyCapsule_Type"
   "PyCell_Type"
   "PyCFunction_Type"
   "PyClassMethodDescr_Type"
   "PyClassMethod_Type"
   "PyCode_Type"
   "PyComplex_Type"
   "PyContextTokenMissing_Type"
   "PyContextToken_Type"
   "PyContext_Type"
   "PyContextVar_Type"
   "PyCoro_Type"
   "PyDictItems_Type"
   "PyDictIterItem_Type"
   "PyDictIterKey_Type"
   "PyDictIterValue_Type"
   "PyDictKeys_Type"
   "PyDictProxy_Type"
   "PyDict_Type"
   "PyDictValues_Type"
   "PyEllipsis_Type"
   "PyEnum_Type"
   "PyFileIO_Type"
   "PyFilter_Type"
   "PyFloat_Type"
   "PyFrame_Type"
   "PyFrozenSet_Type"
   "PyFunction_Type"
   "PyGen_Type"
   "PyGetSetDescr_Type"
   "PyIncrementalNewlineDecoder_Type"
   "PyInstanceMethod_Type"
   "PyIOBase_Type"
   "PyListIter_Type"
   "PyListRevIter_Type"
   "PyList_Type"
   "PyLongRangeIter_Type"
   "PyLong_Type"
   "PyMap_Type"
   "PyMemberDescr_Type"
   "PyMemoryView_Type"
   "PyMethodDescr_Type"
   "PyMethod_Type"
   "PyModuleDef_Type"
   "PyModule_Type"
   "PyODictItems_Type"
   "PyODictIter_Type"
   "PyODictKeys_Type"
   "PyODict_Type"
   "PyODictValues_Type"
   "PyProperty_Type"
   "PyRangeIter_Type"
   "PyRange_Type"
   "PyRawIOBase_Type"
   "PyReversed_Type"
   "PySeqIter_Type"
   "PySetIter_Type"
   "PySet_Type"
   "PySlice_Type"
   "PyStaticMethod_Type"
   "PyStdPrinter_Type"
   "PySTEntry_Type"
   "PyStringIO_Type"
   "PySuper_Type"
   "PyTextIOBase_Type"
   "PyTextIOWrapper_Type"
   "PyTraceBack_Type"
   "PyTupleIter_Type"
   "PyTuple_Type"
   "PyType_Type"
   "PyUnicodeIter_Type"
   "PyUnicode_Type"
   "PyWrapperDescr_Type"
   "PyZip_Type"])


(def type-symbol-table
  (->> type-symbol-names
       (map (juxt (comp keyword ->kebab-case) identity))
       (into {})))


;; (-> (pyobj/PyObject_GetAttrString symbol-addr "__name__")
;;                                   (pyuni/PyUnicode_AsUTF8)
;;                                   (jna/variable-byte-ptr->string)
;;                                   keyword)


(defn get-type-name
  [type-pyobj]
  (-> (pyobj/PyObject_GetAttrString type-pyobj "__name__")
      (pyuni/PyUnicode_AsUTF8)
      (jna/variable-byte-ptr->string)
      ->kebab-case
      keyword))


(defn lookup-type-symbols
  "Transform the static type-symbol-table map into a map of actual long pointer addresses to
  {:typename :type-symbol-name}"
  []
  (->> type-symbol-table
       (map (fn [[typename type-symbol-name]]
              (try
                (when-let [symbol-addr (find-pylib-symbol type-symbol-name)]
                  [(Pointer/nativeValue symbol-addr)
                   {:typename (get-type-name symbol-addr)
                    :type-symbol-name type-symbol-name}])
                (catch Throwable e nil))))
       (remove nil?)
       (into {})))


(defn Py_None
  []
  (PyObject. (find-pylib-symbol "_Py_NoneStruct")))


(defn Py_NotImplemented
  []
  (PyObject. (find-pylib-symbol "_Py_NotImplementedStruct")))

;; Interpreter level protocols

(def-pylib-fn PyMem_Free
  "Frees the memory block pointed to by p, which must have been returned by a previous
  call to PyMem_Malloc(), PyMem_Realloc() or PyMem_Calloc(). Otherwise, or if
  PyMem_Free(p) has been called before, undefined behavior occurs.

   If p is NULL, no operation is performed."
  nil
  [data-ptr jna/as-ptr])


;;Acquire the GIL of the given thread state
(def-pylib-fn PyEval_RestoreThread
  "Acquire the global interpreter lock (if it has been created and thread support is
  enabled) and set the thread state to tstate, which must not be NULL. If the lock has
  been created, the current thread must not have acquired it, otherwise deadlock ensues.

  Note

  Calling this function from a thread when the runtime is finalizing will terminate the
  thread, even if the thread was not created by Python. You can use _Py_IsFinalizing()
  or sys.is_finalizing() to check if the interpreter is in process of being finalized
  before calling this function to avoid unwanted termination."
  nil
  [tstate ensure-pyobj])


;;Release the GIL, return thread state
(def-pylib-fn PyEval_SaveThread
  "Release the global interpreter lock (if it has been created and thread support is
  enabled) and reset the thread state to NULL, returning the previous thread state
  (which is not NULL). If the lock has been created, the current thread must have
  acquired it."
  Pointer)


;;Get current thread state for interpreter
(def-pylib-fn PyThreadState_Get
  "Return the current thread state. The global interpreter lock must be held. When the
  current thread state is NULL, this issues a fatal error (so that the caller needn’t
  check for NULL)."
  Pointer)


;;Swap threadstate for new interpreter without releasing gil
(def-pylib-fn PyThreadState_Swap
  "Swap the current thread state with the thread state given by the argument tstate,
  which may be NULL. The global interpreter lock must be held and is not released."
  Pointer
  [tstate ensure-pyobj])


(def start-symbol-table
  {:py-single-input 256
   :py-file-input 257
   :py-eval-input 258})


(defn start-symbol
  [item]
  (let [value (cond
               (number? item)
               (long item)
               (keyword? item)
               (get start-symbol-table item 0))
        valid-values (set (vals start-symbol-table))]
    (when-not (contains? valid-values value)
      (throw (ex-info (format "%s is not a start symbol" item) {})))
    (int value)))


(def-pylib-fn PyRun_String
  "Return value: New reference.

   Execute Python source code from str in the context specified by the objects globals
   and locals with the compiler flags specified by flags. globals must be a dictionary;
   locals can be any object that implements the mapping protocol. The parameter start
   specifies the start token that should be used to parse the source code.

   Returns the result of executing the code as a Python object, or NULL if an exception
   was raised."
  PyObject
  [program str]
  [start-sym start-symbol]
  [globals ensure-pydict]
  ;;Any object that implements the mapping protocol
  [locals ensure-pydict])


(def-pylib-fn PyRun_StringFlags
  "Return value: New reference.

   Execute Python source code from str in the context specified by the objects globals
   and locals with the compiler flags specified by flags. globals must be a dictionary;
   locals can be any object that implements the mapping protocol. The parameter start
   specifies the start token that should be used to parse the source code.

   Returns the result of executing the code as a Python object, or NULL if an exception
   was raised."
  PyObject
  [program str]
  [start-sym start-symbol]
  [globals ensure-pydict]
  ;;Any object that implements the mapping protocol
  [locals ensure-pydict]
  [compilerflags identity])
