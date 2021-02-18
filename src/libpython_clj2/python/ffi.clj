(ns libpython-clj2.python.ffi
  "Low level bindings to the python shared library system."
  (:require [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.struct :as dt-struct]
            [tech.v3.datatype.errors :as errors]
            [tech.v3.datatype.native-buffer :as native-buffer]
            [tech.v3.resource :as resource]
            [libpython-clj.python.gc :as pygc]
            [camel-snake-kebab.core :as csk]
            [clojure.tools.logging :as log])
  (:import [java.util.concurrent.atomic AtomicLong]
           [java.util.concurrent ConcurrentHashMap]
           [java.util.function Function]
           [tech.v3.datatype.ffi Pointer]))


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
   :PySys_SetArgv {:rettype :void
                   :argtypes [['argc :int32] ['argv :pointer]]
                   :doc "Set the argv/argc for the interpreter.
Required for some python modules"}
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
   :PyObject_GetAttrString {:rettype :pointer
                            :argtypes [['obj :pointer]
                                       ['attname :string]]
                            :doc "Return an attribute via string name"}
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
                      :doc "Get the module dictionary"}})

(def size-t-type (dt-ffi/size-t-type))


(def python-lib-def (dt-ffi/define-library python-library-fns))
(def pyobject-struct-type (dt-struct/define-datatype!
                            :pyobject [{:name :ob_refcnt :datatype size-t-type}
                                       {:name :ob_type :datatype size-t-type}]))

(def ^{:tag 'long} pytype-offset
  (first (dt-struct/offset-of pyobject-struct-type :ob_type)))

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


(defn library-loaded? [] (not (nil? @library*)))


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


(defn initialize!
  [libpath & [{:keys [signals?]
               :or {signals? true}}]]
  (set-library! libpath)
  (when-not (= 1 (Py_IsInitialized))
    (log/debug "Initializing Python C Layer")
    (Py_InitializeEx (if signals? 1 0))
    (PyEval_SaveThread))
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


(comment
  (do
    (initialize! "python3.8")
    (def test-module (with-gil (PyImport_AddModule "__main__")))
    )
  )


(defn pyobject-type
  ^Pointer [pobj]
  (if (= :int32 (dt-ffi/size-t-type))
    (Pointer. (.getInt (native-buffer/unsafe)
                       (+ (.address (dt-ffi/->pointer pobj)) pytype-offset)))
    (Pointer. (.getLong (native-buffer/unsafe)
                        (+ (.address (dt-ffi/->pointer pobj)) pytype-offset)))))


(defn pytype-name
  ^String [type-pyobj]
  (with-gil
    (if-let [obj-name (PyObject_GetAttrString type-pyobj "__name__")]
      (-> (PyUnicode_AsUTF8 obj-name)
          (dt-ffi/c->string))
      (do
        (log/warn "Failed to get typename for object")
        "failed-typename-lookup"))))


(def ^{:tag ConcurrentHashMap} type-addr->typename-kwd (ConcurrentHashMap.))


(defn pyobj-type-kwd
  [pyobject]
  (let [pytype (pyobject-type pyobject)]
    (.computeIfAbsent type-addr->typename-kwd
                      (.address pytype)
                      (reify Function
                        (apply [this type-addr]
                          (-> (pytype-name pytype)
                              (csk/->kebab-case-keyword)))))))


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
          globals (or globals (PyModule_GetDict main-mod))
          locals (or locals globals)
          retval (PyRun_String (str program)
                               (start-symbol :py-file-input)
                               globals locals)]
      {:globals globals
       :locals locals
       :retval retval})))
