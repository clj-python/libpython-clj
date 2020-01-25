(ns libpython-clj.jna.interpreter
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     def-no-gil-pylib-fn
                     as-pyobj
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
            [camel-snake-kebab.core :refer [->kebab-case]]
            [clojure.tools.logging :as log])
  (:import [com.sun.jna Pointer Native NativeLibrary]
           [com.sun.jna.ptr PointerByReference]
           [libpython_clj.jna PyObject DirectMapped]))



(def-no-gil-pylib-fn Py_SetProgramName
  "This function should be called before Py_Initialize() is called for the first time,
  if it is called at all. It tells the interpreter the value of the argv[0] argument to
  the main() function of the program (converted to wide characters). This is used by
  Py_GetPath() and some other functions below to find the Python run-time libraries
  relative to the interpreter executable. The default value is 'python'. The argument
  should point to a zero-terminated wide character string in static storage whose
  contents will not change for the duration of the program’s execution. No code in the
  Python interpreter will change the contents of this storage.

  Use Py_DecodeLocale() to decode a bytes string to get a wchar_* string."
  nil
  [name jna/ensure-ptr])




(def-no-gil-pylib-fn Py_SetPythonHome
  "Set the default “home” directory, that is, the location of the standard Python
  libraries. See PYTHONHOME for the meaning of the argument string.

  The argument should point to a zero-terminated character string in static storage
  whose contents will not change for the duration of the program’s execution. No code
  in the Python interpreter will change the contents of this storage.

  Use Py_DecodeLocale() to decode a bytes string to get a wchar_* string."
  nil
  [home jna/ensure-ptr])



;; Bugs and caveats: The destruction of modules and objects in modules is done in random
;; order; this may cause destructors (__del__() methods) to fail when they depend on
;; other objects (even functions) or modules. Dynamically loaded extension modules
;; loaded by Python are not unloaded. Small amounts of memory allocated by the Python
;; interpreter may not be freed (if you find a leak, please report it). Memory tied up
;; in circular references between objects is not freed. Some memory allocated by
;; extension modules may not be freed. Some extensions may not work properly if their
;; initialization routine is called more than once; this can happen if an application
;; calls Py_Initialize() and Py_Finalize() more than once.


(def-no-gil-pylib-fn Py_InitializeEx
  "This function works like Py_Initialize() if initsigs is 1. If initsigs is 0, it skips
  initialization registration of signal handlers, which might be useful when Python is
  embedded."
  nil
  [initsigs int])



(def-no-gil-pylib-fn Py_IsInitialized
  "Return true (nonzero) when the Python interpreter has been initialized, false (zero)
  if not. After Py_Finalize() is called, this returns false until Py_Initialize() is
  called again."
  Integer)


(def-no-gil-pylib-fn Py_FinalizeEx
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

;; System Functionality

(def-no-gil-pylib-fn PySys_SetArgv
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
  (if-let [obj-name (pyobj/PyObject_GetAttrString type-pyobj "__name__")]
    (-> (pyuni/PyUnicode_AsUTF8 obj-name)
        (jna/variable-byte-ptr->string)
        ->kebab-case
        keyword)
    (do
      (log/warn "Failed to get typename for object")
      :typename-lookup-failure)))


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
  (find-pylib-symbol "_Py_NoneStruct"))


(defn Py_NotImplemented
  []
  (find-pylib-symbol "_Py_NotImplementedStruct"))

;; Interpreter level protocols

(def-pylib-fn PyMem_Free
  "Frees the memory block pointed to by p, which must have been returned by a previous
  call to PyMem_Malloc(), PyMem_Realloc() or PyMem_Calloc(). Otherwise, or if
  PyMem_Free(p) has been called before, undefined behavior occurs.

   If p is NULL, no operation is performed."
  nil
  [data-ptr jna/as-ptr])


(defn PyGILState_Ensure
  "Ensure that the current thread is ready to call the Python C API regardless of
   the current state of Python, or of the global interpreter lock. This may be called
   as many times as desired by a thread as long as each call is matched with a call
   to PyGILState_Release(). In general, other thread-related APIs may be used between
   PyGILState_Ensure() and PyGILState_Release() calls as long as the thread state is
   restored to its previous state before the Release(). For example, normal usage of
   the Py_BEGIN_ALLOW_THREADS and Py_END_ALLOW_THREADS macros is acceptable.

   The return value is an opaque “handle” to the thread state when PyGILState_Ensure()
   was called, and must be passed to PyGILState_Release() to ensure Python is left in 
   the same state. Even though recursive calls are allowed, these handles cannot be shared -
   each unique call to PyGILState_Ensure() must save the handle for its call to PyGILState_Release().

   When the function returns, the current thread will hold the GIL and be able to call
   arbitrary Python code. Failure is a fatal error."
  ^long []
  (DirectMapped/PyGILState_Ensure))

(defn PyGILState_Release
  "Release any resources previously acquired. After this call, Python’s state will be
   the same as it was prior to the corresponding PyGILState_Ensure() call (but generally
   this state will be unknown to the caller, hence the use of the GILState API).

   Every call to PyGILState_Ensure() must be matched by a call to PyGILState_Release()
   on the same thread."
  [^long s]
  (DirectMapped/PyGILState_Release s))

(defn PyGILState_Check
  "Return 1 if the current thread is holding the GIL and 0 otherwise. This function
  can be called from any thread at any time. Only if it has had its Python thread
  state initialized and currently is holding the GIL will it return 1. This is
  mainly a helper/diagnostic function. It can be useful for example in callback
  contexts or memory allocation functions when knowing that the GIL is locked can
  allow the caller to perform sensitive actions or otherwise behave differently."
  ^long []
  (DirectMapped/PyGILState_Check))


;;Acquire the GIL of the given thread state
(defn PyEval_RestoreThread
  "Acquire the global interpreter lock (if it has been created and thread support is
  enabled) and set the thread state to tstate, which must not be NULL. If the lock has
  been created, the current thread must not have acquired it, otherwise deadlock ensues.

  Note

  Calling this function from a thread when the runtime is finalizing will terminate the
  thread, even if the thread was not created by Python. You can use _Py_IsFinalizing()
  or sys.is_finalizing() to check if the interpreter is in process of being finalized
  before calling this function to avoid unwanted termination."
  [tstate]
  (DirectMapped/PyEval_RestoreThread (ensure-pyobj tstate)))


;;Release the GIL, return thread state
(defn PyEval_SaveThread
  "Release the global interpreter lock (if it has been created and thread support is
  enabled) and reset the thread state to NULL, returning the previous thread state
  (which is not NULL). If the lock has been created, the current thread must have
  acquired it."
  ^Pointer []
  (DirectMapped/PyEval_SaveThread))


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
  Pointer
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
  Pointer
  [program str]
  [start-sym start-symbol]
  [globals ensure-pydict]
  ;;Any object that implements the mapping protocol
  [locals ensure-pydict]
  [compilerflags identity])
