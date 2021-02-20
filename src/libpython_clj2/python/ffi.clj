(ns libpython-clj2.python.ffi
  "Low level bindings to the python shared library system."
  (:require [tech.v3.datatype :as dtype]
            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.struct :as dt-struct]
            [tech.v3.datatype.errors :as errors]
            [tech.v3.datatype.native-buffer :as native-buffer]
            [tech.v3.resource :as resource]
            [libpython-clj.python.gc :as pygc]
            [camel-snake-kebab.core :as csk]
            [clojure.tools.logging :as log]
            [clojure.string :as s])
  (:import [java.util.concurrent.atomic AtomicLong]
           [java.util.concurrent ConcurrentHashMap]
           [java.util.function Function]
           [tech.v3.datatype.ffi Pointer Library]))


(set! *warn-on-reflection* true)


(declare wrap-pyobject)


(def python-library-fns
  {:Py_InitializeEx {:rettype :void
                     :argtypes [['signals :int32]]
                     :doc "Initialize the python shared library"}
   :Py_IsInitialized {:rettype :int32
                      :doc "Return 1 if library is initalized, 0 otherwise"}

   :PyRun_SimpleString {:rettype :int32
                        :argtypes [['argstr :string]]
                        :doc "Low-level run a simple python string."}
   :PyRun_String {:rettype :pointer
                  :argtypes [['program :string]
                             ['start-sym :int32]
                             ['globals :pointer]
                             ['locals :pointer]]
                  :doc "Run a string setting the start type, globals and locals"}
   :PySys_SetArgvEx {:rettype :void
                     :argtypes [['argc :int32]
                                ['argv-wide-ptr-ptr :pointer]
                                ['update :int32]]
                   :doc "Set the argv/argc for the interpreter.
Required for some python modules"}
   :Py_SetProgramName {:rettype :void
                       :argtypes [['program-name-wideptr :pointer]]
                       :doc "Set the program name"}
   :PyEval_SaveThread {:rettype :pointer
                       :doc "Release the GIL on the current thread"}
   :PyGILState_Ensure {:rettype :int32
                       :doc "Ensure this thread owns the python GIL.
Each call must be matched with PyGILState_Release"}
   :PyGILState_Check {:rettype :int32
                      :doc "Return 1 if gil is held, 0 otherwise"}
   :PyGILState_Release {:rettype :void
                        :argtypes [['modhdl :int32]]
                        :doc "Release the GIL state."}
   :Py_IncRef {:rettype :void
               :argtypes [['pyobj :pointer]]
               :doc "Increment the reference count on a pyobj"}
   :Py_DecRef {:rettype :void
               :argtypes [['pyobj :pointer]]
               :doc "Decrement the reference count on a pyobj"}
   :PyErr_Occurred {:rettype :pointer
                    :doc "Return the current in-flight exception without clearing it."}
   :PyErr_Fetch {:rettype :void
                 :argtypes [['type :pointer]
                            ['value :pointer]
                            ['tb :pointer]]
                 :doc "Fetch and clear the current exception information"}
   :PyErr_NormalizeException {:rettype :void
                              :argtypes [['type :pointer]
                                         ['value :pointer]
                                         ['tb :pointer]]
                              :doc "Normalize a python exception."}
   :PyException_SetTraceback {:rettype :int32
                              :argtypes [['val :pointer]
                                         ['tb :pointer]]
                              :doc "Set the traceback on the exception object"}
   :PyUnicode_AsUTF8 {:rettype :pointer
                      :argtypes [['obj :pointer]]
                      :doc "convert a python unicode object to a utf8 encoded string"}
   :PyImport_ImportModule {:rettype :pointer
                           :argtypes [['modname :string]]
                           :doc "Import a python module"}
   :PyImport_AddModule {:rettype :pointer
                        :argtypes [['modname :string]]
                        :doc "Add a python module"}
   :PyModule_GetDict {:rettype :pointer
                      :argtypes [['module :pointer]]
                      :doc "Get the module dictionary"}
   :PyObject_Dir {:rettype :pointer
                  :argtypes [['pyobj :pointer]]
                  :doc "Get a python sequence of string attribute names"}
   :PyObject_HasAttr {:rettype :int32
                      :argtypes [['o :pointer]
                                 ['attr_name :pointer]]
                      :doc "Return 1 if object has an attribute"}
   :PyObject_HasAttrString {:rettype :int32
                            :argtypes [['o :pointer]
                                       ['attr_name :string]]
                            :doc "Return 1 if object has an attribute"}
   :PyObject_GetAttr {:rettype :pointer
                      :argtypes [['o :pointer]
                                 ['attr_name :pointer]]
                      :doc "get an attribute from an object"}
   :PyObject_GetAttrString {:rettype :pointer
                            :argtypes [['o :pointer]
                                       ['attr_name :string]]
                            :doc "get an attribute from an object"}
   :PyObject_SetAttr {:rettype :int32
                      :argtypes [['o :pointer]
                                 ['attr_name :pointer]
                                 ['v :pointer]]
                      :doc "Set an attribute on a python object"}
   :PyObject_Call {:rettype :pointer
                   :argtypes [['callable :pointer]
                              ['args :pointer]
                              ['kwargs :pointer?]]
                   :doc "Call a callable object"}
   :PyObject_CallObject {:rettype :pointer
                   :argtypes [['callable :pointer]
                              ['args :pointer?]]
                   :doc "Call a callable object with no kwargs.  args may be nil"}
   :PyObject_SetAttrString {:rettype :int32
                            :argtypes [['o :pointer]
                                       ['attr_name :string]
                                       ['v :pointer]]}
   :PyMapping_Check {:rettype :int32
                     :argtypes [['pyobj :pointer]]
                     :doc "Check if this object implements the mapping protocol"}
   :PyMapping_Items {:rettype :pointer
                     :argtypes [['pyobj :pointer]]
                     :doc "Get an iterable of tuples of this map."}
   :PySequence_Check {:rettype :int32
                      :argtypes [['pyobj :pointer]]
                      :doc "Check if this object implements the sequence protocol"}
   :PySequence_Length {:rettype :size-t
                       :argtypes [['pyobj :pointer]]
                       :doc "Get the length of a sequence"}
   :PySequence_GetItem {:rettype :pointer
                        :argtypes [['pyobj :pointer]
                                   ['idx :size-t]]
                        :doc "Get a specific item from a sequence"}
   :PyFloat_AsDouble {:rettype :float64
                     :argtypes [['pyobj :pointer]]
                       :doc "Get a double value from a python float"}
   :PyFloat_FromDouble {:rettype :pointer
                       :argtypes [['data :float64]]
                       :doc "Get a pyobject form a long."}

   :PyLong_AsLongLong {:rettype :int64
                       :argtypes [['pyobj :pointer]]
                       :doc "Get the long value from a python integer"}
   :PyLong_FromLongLong {:rettype :pointer
                         :argtypes [['data :int64]]
                         :doc "Get a pyobject form a long."}
   :PyDict_Next {:rettype :int32
                 :argtypes [['pyobj :pointer]
                            ['ppos :pointer]
                            ['pkey :pointer]
                            ['pvalue :pointer]]
                 :doc "Get the next value from a dictionary"}
   :PyTuple_New {:rettype :pointer
                 :argtypes [['len :size-t]]
                 :doc "Create a new uninitialized tuple"}
   :PyTuple_SetItem {:rettype :int32
                     :argtypes [['tuple :pointer]
                                ['idx :size-t]
                                ['pvalue :pointer]]
                     :doc "Insert a reference to object o at position pos of the tuple pointed to by p. Return 0 on success. If pos is out of bounds, return -1 and set an IndexError exception."}
   :PyErr_Clear {:rettype :void
                 :doc "Clear the current python error"}})


(defonce size-t-type (dt-ffi/size-t-type))


(def python-lib-def (dt-ffi/define-library python-library-fns))
(defonce pyobject-struct-type (dt-struct/define-datatype!
                                :pyobject [{:name :ob_refcnt :datatype size-t-type}
                                           {:name :ob_type :datatype size-t-type}]))

(def ^{:tag 'long} pytype-offset
  (first (dt-struct/offset-of pyobject-struct-type :ob_type)))


(def ^{:tag 'long} pyrefcnt-offset
  (first (dt-struct/offset-of pyobject-struct-type :ob_refcnt)))

(defn ptr->struct
  [struct-type ptr-type]
  (let [n-bytes (:datatype-size (dt-struct/get-struct-def struct-type))
        src-ptr (dt-ffi/->pointer ptr-type)
        nbuf (native-buffer/wrap-address (.address src-ptr)
                                         n-bytes
                                         src-ptr)]
    (dt-struct/inplace-new-struct struct-type nbuf)))


(defonce ^:private library* (atom nil))
(defonce ^:private library-path* (atom nil))



(defn set-library!
  [libpath]
  (when @library*
    (log/warnf "Python library is being reinitialized to (%s).  Is this what you want?"
               libpath))

  (reset! library* (dt-ffi/instantiate-library python-lib-def libpath))
  (reset! library-path* libpath))


(defn reset-library!
  []
  (when @library-path*
    (reset! library* (dt-ffi/instantiate-library python-lib-def @library-path*))))

;;Useful for repling around - this regenerates the library function bindings
(reset-library!)


(defn library-loaded? [] (not (nil? @library*)))

(defn current-library
  ^Library []
  @library*)


(defn- find-pylib-fn
  [fn-kwd]
  (let [pylib @library*]
    (errors/when-not-error
     pylib
     "Library not found.  Has set-library! been called?")
    (if-let [retval (fn-kwd @pylib)]
      retval
      (errors/throwf "Python function %s not found" (symbol (name fn-kwd))))))


(defmacro def-py-fn
  [fn-name docs & args]
  (let [fn-kwd (keyword (name fn-name))]
    (errors/when-not-errorf
     (contains? python-library-fns fn-kwd)
     "Python function %s is not defined" fn-name)
    `(defn ~fn-name ~docs
       ~(vec (map first args))
       (let [retval#
             (resource/stack-resource-context
              ((find-pylib-fn ~fn-kwd) ~@(map (fn [[argname marshal-fn]]
                                                `(~marshal-fn ~argname))
                                              args)))]))))


(defmacro define-pylib-functions
  []
  `(do
     ~@(->>
        python-library-fns
        (map
         (fn [[fn-name {:keys [rettype argtypes] :as fn-data}]]
           (let [fn-symbol (symbol (name fn-name))
                 requires-resctx? (first (filter #(= :string %)
                                                 (map second argtypes)))]
             `(defn ~fn-symbol
                ~(:doc fn-data "No documentation!")
                ~(mapv first argtypes)
                (let [~'ifn (find-pylib-fn ~fn-name)]
                  ~(if requires-resctx?
                     `(resource/stack-resource-context
                       (~'ifn ~@(map (fn [[argname argtype]]
                                       (cond
                                         (#{:int8 :int16 :int32 :int64} argtype)
                                         `(long ~argname)
                                         (#{:float32 :float64} argtype)
                                         `(double ~argname)
                                         (= :string argtype)
                                         `(dt-ffi/string->c ~argname)
                                         :else
                                         argname))
                                     argtypes)))
                     `(~'ifn ~@(map (fn [[argname argtype]]
                                      (cond
                                        (#{:int8 :int16 :int32 :int64} argtype)
                                        `(long ~argname)
                                        (#{:float32 :float64} argtype)
                                        `(double ~argname)
                                        (= :string argtype)
                                        `(dt-ffi/string->c ~argname)
                                        :else
                                        argname))
                                    argtypes)))))))))))


(define-pylib-functions)


(defmacro check-gil
  "Maybe the most important insurance policy"
  []
  `(errors/when-not-error
    (= 1 (PyGILState_Check))
    "GIL is not captured"))


(defmacro with-decref
  [vardefs & body]
  (let [n-vars (count vardefs)]
    (if (= 2 n-vars)
      `(let ~vardefs
         (try
           ~@body
           (finally
             (Py_DecRef ~(first vardefs)))))
      `(let [~'obj-data (object-array ~n-vars)]
         (try
           (let [~@(mapcat (fn [[idx [varsym varform]]]
                             [varsym `(let [vardata# ~varform]
                                        (aset ~'obj-data ~idx vardata#)
                                        vardata#)])
                           (map-indexed vector (partition 2 vardefs)))]
             ~@body)
           (finally
             (dotimes [idx# ~n-vars]
               (when-let [pyobj#  (aget ~'obj-data idx#)]
                 (Py_DecRef pyobj#)))))))))


(defonce ^{:tag ConcurrentHashMap
           :private true}
  forever-map (ConcurrentHashMap.))


(defn retain-forever
  [item-key item-val]
  (.put forever-map item-key item-val)
  item-val)


(defonce format-exc-pyfn* (atom nil))


(defn- init-exc-formatter
  []
  (check-gil)
  (let [tback-mod (PyImport_ImportModule "traceback")
        format-fn (PyObject_GetAttrString tback-mod "format_exception")]
    ;;the tback module cannot be removed from memory and it always has a reference
    ;;to format-fn so we drop our reference.
    (Py_DecRef tback-mod)
    (Py_DecRef format-fn)
    (reset! format-exc-pyfn* format-fn)))


(defn- exc-formatter
  []
  (when-not @format-exc-pyfn*
    (init-exc-formatter))
  @format-exc-pyfn*)


(defn initialize!
  [libpath python-home & [{:keys [signals? program-name]
                           :or {signals? true
                                program-name ""}}]]
  (set-library! libpath)
  (when-not (= 1 (Py_IsInitialized))
    (log/debug "Initializing Python C Layer")
    (let [program-name (retain-forever :program-name
                                       (-> (or program-name "")
                                           (dt-ffi/string->c :utf-16)))
          wide-ptr (retain-forever
                    :program-name-ptr-ptr
                    (dtype/make-container :native-heap :int64
                                          [(.address (dtype/as-native-buffer
                                                      program-name))]))]
      (Py_SetProgramName program-name)
      (Py_InitializeEx (if signals? 1 0))
      (PySys_SetArgvEx 0 wide-ptr 0)
      ;;return value ignored :-)
      ;;This releases the GIL until further processing and allows with-gil to work
      ;;correctly.
      (PyEval_SaveThread)))
  :ok)


(defonce ^{:tag AtomicLong} gil-thread-id (AtomicLong. Long/MAX_VALUE))


(defmacro with-gil
  "Grab the gil and use the main interpreter using reentrant acquire-gil pathway."
  [& body]
  `(let [[gil-state# prev-id#]
        (locking #'gil-thread-id
          (let [prev-id# (.get gil-thread-id)
                thread-id# (-> (Thread/currentThread)
                               (.getId))]
            (when-not (== prev-id# thread-id#)
              (.set gil-thread-id thread-id#)
              [(PyGILState_Ensure) prev-id#])))]
    (try
      ~@body
      (finally
        (when gil-state#
          (pygc/clear-reference-queue)
          (locking #'gil-thread-id
            (PyGILState_Release gil-state#)
            (.set gil-thread-id prev-id#)))))))


(defn pyobject-type
  ^Pointer [pobj]
  (if (= :int32 (dt-ffi/size-t-type))
    (Pointer. (.getInt (native-buffer/unsafe)
                       (+ (.address (dt-ffi/->pointer pobj)) pytype-offset)))
    (Pointer. (.getLong (native-buffer/unsafe)
                        (+ (.address (dt-ffi/->pointer pobj)) pytype-offset)))))


(defn pyobject-refcount
  ^long [pobj]
  (if (= :int32 (dt-ffi/size-t-type))
    (.getInt (native-buffer/unsafe)
             (+ (.address (dt-ffi/->pointer pobj)) pyrefcnt-offset))
    (.getLong (native-buffer/unsafe)
              (+ (.address (dt-ffi/->pointer pobj)) pyrefcnt-offset))))


(defn pystr->str
  ^String [pyobj]
  (check-gil)
  (-> (PyUnicode_AsUTF8 pyobj)
      (dt-ffi/c->string)))


(defn pytype-name
  ^String [type-pyobj]
  (with-gil
    (if-let [obj-name (PyObject_GetAttrString type-pyobj "__name__")]
      (pystr->str obj-name)
      (do
        (log/warn "Failed to get typename for object")
        "failed-typename-lookup"))))


(defonce ^{:tag ConcurrentHashMap} type-addr->typename-kwd (ConcurrentHashMap.))


(defn pyobject-type-kwd
  [pyobject]
  (let [pytype (pyobject-type pyobject)]
    (.computeIfAbsent type-addr->typename-kwd
                      (.address pytype)
                      (reify Function
                        (apply [this type-addr]
                          (-> (pytype-name pytype)
                              (csk/->kebab-case-keyword)))))))


(def ^{:doc "Dereferences to the value of the py-none symbol"
       :tag Pointer}
  py-none* (delay (.findSymbol (current-library) "_Py_NoneStruct")))

(defn py-none
  ^Pointer []
  @py-none*)

(def ^{:doc "Dereferences to the value of the py-true symbol"
       :tag Pointer}
  py-true* (delay (.findSymbol (current-library) "_Py_TrueStruct")))

(defn py-true
  ^Pointer []
  @py-true*)


(def ^{:doc "Dereferences to the value of the py-false symbol"
       :tag Pointer}
  py-false* (delay (.findSymbol (current-library) "_Py_FalseStruct")))

(defn py-false
  ^Pointer []
  @py-false*)


(defn make-tuple
  "Low-level make tuple fn.  Args must all by python objects."
  [& args]
  (check-gil)
  (let [args (vec args)
        argcount (count args)
        tuple (PyTuple_New argcount)]
    (dotimes [idx argcount]
      (Py_IncRef (args idx))
      (PyTuple_SetItem tuple idx (args idx)))
    tuple))


(defn check-error-str
  "Function assumes python stdout and stderr have been redirected"
  []
  (check-gil)
  (when-not (= nil (PyErr_Occurred))
    (let [type (dt-ffi/make-ptr :pointer 0)
          value (dt-ffi/make-ptr :pointer 0)
          tb (dt-ffi/make-ptr :pointer 0)]
      (PyErr_Fetch type value tb)
      (PyErr_NormalizeException type value tb)
      (let [type (Pointer. (type 0))
            value (Pointer. (value 0))
            tb (Pointer. (tb 0))
            tb (if (.isNil tb) (py-none) tb)]
        (try
          (with-decref [argtuple (make-tuple type value tb)
                        exc-str-tuple (PyObject_CallObject (exc-formatter) argtuple)]
            (->> (range (PySequence_Length exc-str-tuple))
                 (map (fn [idx]
                        (with-decref [strdata (PySequence_GetItem exc-str-tuple idx)]
                          (pystr->str strdata))))
                 (s/join)))
          ;;Apparently we own the reference meaning it is a new reference and we need
          ;;to release those references.
          (finally
            (Py_DecRef type)
            (Py_DecRef value)
            (when-not (= tb (py-none))
              (Py_DecRef tb))))))))


(defn check-error-throw
  []
  (when-let [error-str (check-error-str)]
    (throw (Exception. ^String error-str))))


(def object-reference-logging (atom false))


(defn- wrap-obj-ptr
  "This must be called with the GIL captured"
  [pyobj ^Pointer pyobjptr]
  (let [addr (.address pyobjptr)]
    (when @object-reference-logging
      (log/infof "tracking object  - 0x%x:%4d:%s"
                 addr
                 (pyobject-refcount pyobj)
                 (name (pyobject-type-kwd pyobjptr))))
    (pygc/track pyobj
                ;;we know the GIL is captured in this method
                #(try
                   ;;Intentionally overshadow pyobj.  We cannot access it here.
                   (let [pyobjptr (Pointer. addr)]
                     (when @object-reference-logging
                       (let [refcount (pyobject-refcount pyobjptr)
                             typename (name (pyobject-type-kwd pyobjptr))]
                         (if (< refcount 1)
                           (log/errorf "Fatal error -- releasing object - 0x%x:%4d:%s
Object's refcount is bad.  Crash is imminent"
                                       pyobjptr
                                       refcount
                                       typename)
                           (log/infof (format "releasing object - 0x%x:%4d:%s"
                                              addr
                                              refcount
                                              typename)))))
                     (Py_DecRef pyobjptr))
                   (catch Throwable e
                     (log/error e "Exception while releasing object"))))))


(defn wrap-pyobject
  ^Pointer [pyobj & [skip-error-check?]]
  (check-gil)
  (when-let [^Pointer pyobjptr (when pyobj (dt-ffi/->pointer pyobj))]
    (if-not (= (py-none) pyobjptr)
      (wrap-obj-ptr pyobj pyobjptr)
      ;;Py_None is handled separately
      (do
        (Py_DecRef pyobjptr)
        (when-not skip-error-check? (check-error-throw))
        nil))))


(defn incref-wrap-pyobject
  ^Pointer [pyobj]
  (when pyobj
    (Py_IncRef pyobj)
    (wrap-pyobject pyobj)))



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


(defn run-simple-string
  "Run a simple string returning boolean 1 or 0.  Note this will never
  return the result of the expression:

  https://mail.python.org/pipermail/python-list/1999-April/018011.html

  Implemented in cpython as:

    PyObject *m, *d, *v;
    m = PyImport_AddModule(\"__main__\");
    if (m == NULL)
        return -1;
    d = PyModule_GetDict(m);
    v = PyRun_StringFlags(command, Py_file_input, d, d, flags);
    if (v == NULL) {
        PyErr_Print();
        return -1;
    }
    Py_DECREF(v);
    return 0;"
  [program & {:keys [globals locals]}]
  (with-gil
    (let [main-mod (PyImport_AddModule "__main__")
          globals (or globals (incref-wrap-pyobject (PyModule_GetDict main-mod)))
          locals (or locals globals)
          retval (wrap-pyobject
                  (PyRun_String (str program)
                                (start-symbol :py-file-input)
                                globals locals))]
      {:globals globals
       :locals locals
       :retval retval})))
