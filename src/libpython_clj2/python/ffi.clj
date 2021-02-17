(ns libpython-clj2.python.ffi
  "Low level bindings to the python shared library system."
  (:require [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.struct :as dt-struct]
            [tech.v3.datatype.errors :as errors]
            [tech.v3.resource :as resource]
            [libpython-clj.python.gc :as pygc]
            [clojure.tools.logging :as log])
  (:import [java.util.concurrent.atomic AtomicLong]))


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
   :PyGILState_Ensure {:rettype :size-t
                       :doc "Ensure this thread owns the python GIL.
Each call must be matched with PyGILState_Release"}
   :PyGILState_Check {:rettype :size-t}
   :PyGILState_Release {:rettype :void
                        :argtypes [['modhdl :size-t]]
                        :doc "Release the GIL state."}
   :PyImport_ImportModule {:rettype :pointer
                           :argtypes [['modname :string]]
                           :doc "Import a python module"}
   :PyImport_AddModule {:rettype :pointer
                        :argtypes [['modname :string]]
                        :doc "Add a python module"}
   :PyModule_GetDict {:rettype :pointer
                      :argtypes [['module :pointer]]}})


(def python-lib-def (dt-ffi/define-library python-library-fns))


(defonce ^:private library* (atom nil))
(defonce ^:private library-path* (atom "python3.8"))


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
          (let [prev-id# (.get #'gil-thread-id)
                thread-id# (-> (Thread/currentThread)
                               (.getId))]
            (when-not #(== prev-id# thread-id#)
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
