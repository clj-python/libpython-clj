(ns libpython-clj.python.interop
  "The messy details of actual embedding python in the jvm, aside from interpreter
  state, go here.  Don't expect a pleasant ride.  If you want to create a new python
  type or function, something that requires knowledge of the C structures behind
  everything that knowledge should be encoded in this file."
  (:require [libpython-clj.jna :as libpy]
            [libpython-clj.python.logging
             :refer [log-error log-warn log-info]]
            [libpython-clj.python.interpreter
             :refer
             [get-object-handle
              with-gil with-interpreter
              ensure-bound-interpreter
              ensure-interpreter
              find-jvm-bridge-entry
              get-jvm-bridge
              unregister-bridge!
              register-bridge!
              conj-forever!]
             :as pyinterp]
            [libpython-clj.python.object
             :refer [copy-to-jvm
                     copy-to-python]]
            [clojure.stacktrace :as st]
            [tech.jna :as jna]
            [libpython-clj.python.object
             :refer [wrap-pyobject incref-wrap-pyobject
                     incref
                     copy-to-jvm copy-to-python
                     ->py-dict
                     set-attr
                     get-attr
                     py-none
                     ->py-string
                     ->py-list
                     ->py-tuple]])
  (:import [java.lang.reflect Field Method]
           [com.sun.jna Pointer Structure CallbackReference]
           [libpython_clj.jna
            CFunction$KeyWordFunction
            CFunction$TupleFunction
            CFunction$NoArgFunction
            CFunction$tp_new
            CFunction$tp_dealloc
            CFunction$tp_att_getter
            CFunction$tp_att_setter
            CFunction$tp_getattr
            CFunction$tp_getattro
            CFunction$tp_setattr
            CFunction$tp_setattro
            CFunction$tp_hash
            PyMethodDef PyObject
            PyMethodDef$ByReference
            PyTypeObject
            JVMBridge
            JVMBridgeType]
           [java.io Writer]))


(set! *warn-on-reflection* true)


(defn wrap-clojure-fn
  [fn-obj]
  (when-not (fn? fn-obj)
    (throw (ex-info "This is not a function." {})))
  (reify CFunction$TupleFunction
    (pyinvoke [this self args]
      (try
        (if-let [retval
                 (apply fn-obj (copy-to-jvm args))]
          (copy-to-python retval)
          (libpy/Py_None))
        (catch Throwable e
          (log-error     (format "%s:%s" e (with-out-str
                                                     (st/print-stack-trace e))) )
          (libpy/PyErr_SetString (libpy/PyExc_Exception)
                                 (format "%s:%s" e (with-out-str
                                                     (st/print-stack-trace e))))
          nil)))))


(defn- apply-method-def-data!
  [^PyMethodDef method-def {:keys [name
                                   doc
                                   function]
                            :as method-data}]
  (let [callback (if (or (instance? CFunction$KeyWordFunction function)
                         (instance? CFunction$TupleFunction function)
                         (instance? CFunction$NoArgFunction function))
                     function
                     (wrap-clojure-fn function))
        meth-flags (long (cond
                           (instance? CFunction$NoArgFunction callback)
                           @libpy/METH_NOARGS

                           (instance? CFunction$TupleFunction callback)
                           @libpy/METH_VARARGS

                           (instance? CFunction$KeyWordFunction callback)
                           (bit-or @libpy/METH_KEYWORDS @libpy/METH_VARARGS)
                           :else
                           (throw (ex-info (format "Failed due to type: %s" (type callback))))))
        name-ptr (jna/string->ptr name)
        doc-ptr (jna/string->ptr doc)]
    (println "meth-flags" meth-flags)
    (set! (.ml_name method-def) name-ptr)
    (set! (.ml_meth method-def) (CallbackReference/getFunctionPointer callback))
    (set! (.ml_flags method-def) (int meth-flags))
    (set! (.ml_doc method-def) doc-ptr)
    (.write method-def)
    (pyinterp/conj-forever! (assoc method-data
                                   :name-ptr name-ptr
                                   :doc-ptr doc-ptr
                                   :callback-object callback
                                   :method-definition method-def))
    method-def))


(defn method-def-data->method-def
  [method-data]
  (apply-method-def-data! (PyMethodDef.) method-data))


(defn create-function
  "Create a python callback from a clojure fn.
  If clojure fn, then tuple arguments are used.  If keyword arguments are desired,
  the pass in something derived from: libpython-clj.jna.CFunction$KeyWordFunction.
  If a pure fn is passed in, arguments are marshalled from python if possible and
  then to-python in the case of successful execution.  An exception will set the error
  indicator."
  [fn-obj & {:keys [method-name documentation py-self]
             :or {method-name "unnamed_function"
                  documentation "not documented"}}]
  (with-gil
    (let [py-self (or py-self (copy-to-python {}))]
      (wrap-pyobject (libpy/PyCFunction_New (method-def-data->method-def
                                             {:name method-name
                                              :doc documentation
                                              :function fn-obj})
                                            ;;This is a nice little tidbit, cfunction_new
                                            ;;steals the reference.
                                            (libpy/Py_IncRef py-self))))))


(defn py-import-module
  [modname]
  (with-gil
    (wrap-pyobject
     (libpy/PyImport_ImportModule modname))))


(defn py-add-module
  [modname]
  (with-gil
    (incref-wrap-pyobject
     (libpy/PyImport_AddModule modname))))


(defn run-simple-string
  "Run a simple string returning boolean 1 or 0.
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
  [^String program]
  (with-gil
    (libpy/PyRun_SimpleString program)))


(defn run-string
  "If you don't know what you are doing, this will sink you.  See documentation for
  run-simple-string."
  [^String program & {:keys [globals locals]}]
  (with-gil
    (let [globals (or globals (->py-dict {}))
          locals (or locals (->py-dict {}))]
      {:result
       (wrap-pyobject
        (libpy/PyRun_String (str program) :py-file-input globals locals))
       :globals globals
       :locals locals})))


(defn test-cpp-example
  []
  (with-gil
    (let [python-script "result = multiplicand * multiplier\n"
          local-dict (->py-dict {"multiplicand" 2
                                 "multiplier" 5})]
      (run-string python-script :locals local-dict))))


(defn create-module
  [modname & {:keys [package docstring unregistered?]}]
  (with-gil
    (let [new-module (wrap-pyobject (libpy/PyModule_New modname))]
      (when docstring
        (libpy/PyModule_SetDocString new-module docstring))
      (when package
        (set-attr new-module "__package__" (->py-string package)))
      new-module)))


(def fieldOffsetMethod
  (memoize
   (fn []
     (doto (.getDeclaredMethod Structure "fieldOffset"
                               (into-array Class [String]))
       (.setAccessible true)))))


(defn offsetof
  [structure-instance fieldname]
  (.invoke ^Method (fieldOffsetMethod)
           structure-instance
           (into-array Object [(str fieldname)])))


(defn create-python-type
  [type-name module-name method-def-data])


(defn method-def-data-seq->method-def-ref
  ^PyMethodDef$ByReference [method-def-data-seq]
  (when (seq method-def-data-seq)
    (let [n-elems (count method-def-data-seq)
          method-def-ary (-> (PyMethodDef.)
                             (.toArray (inc n-elems)))]
      (->> method-def-data-seq
           (map-indexed (fn [idx def-data]
                          (apply-method-def-data!
                           (aget method-def-ary idx)
                           def-data)))
           dorun)
      (PyMethodDef$ByReference. (.getPointer ^PyMethodDef
                                             (aget method-def-ary 0))))))


(defn register-type!
  "Register a new type.  Please refer to python documentation for meaning
  of the variables."
  [module {:keys [type-name
                  docstring
                  method-definitions
                  tp_flags ;;may be nil
                  tp_basicsize ;;size of binary type
                  tp_new ;;may be nil, will use generic
                  tp_dealloc ;;may be nil, will use generic
                  tp_getattr ;;may *not* be nil
                  tp_setattr ;;may *not* be nil
                  ]
           :as type-definition}]
  (when (or (not type-name)
            (= 0 (count type-name)))
    (throw (ex-info "Cannot create unnamed type." {})))
  (let [tp_new (or tp_new
                   (reify CFunction$tp_new
                     (pyinvoke [this self varargs kw_args]
                       (libpy/PyType_GenericNew self varargs kw_args))))
        module-name (-> (get-attr module "__name__")
                        (copy-to-jvm))
        docstring-ptr (jna/string->ptr docstring)
        type-name-ptr (jna/string->ptr (str module-name "." type-name))
        tp_flags (long (or tp_flags
                           (bit-or @libpy/Py_TPFLAGS_DEFAULT
                                   @libpy/Py_TPFLAGS_BASETYPE)))
        new-type (PyTypeObject.)]
    (set! (.tp_name new-type) type-name-ptr)
    (set! (.tp_doc new-type) docstring-ptr)
    (set! (.tp_basicsize new-type) tp_basicsize)
    (set! (.tp_flags new-type) tp_flags)
    (set! (.tp_new new-type) tp_new)
    (set! (.tp_dealloc new-type) tp_dealloc)
    (set! (.tp_getattr new-type) tp_getattr)
    (set! (.tp_setattr new-type) tp_setattr)
    (set! (.tp_methods new-type) (method-def-data-seq->method-def-ref
                                  method-definitions))
    (let [type-ready (libpy/PyType_Ready new-type)]
      (if (>= 0 type-ready)
        (do
          (libpy/Py_IncRef new-type)
          (.read new-type)
          (libpy/PyModule_AddObject module (str type-name "_type") new-type)
          ;;We are careful to keep track of the static data we give to python.
          ;;the GC cannot now remove any of this stuff pretty much
          ;;forever now.
          (conj-forever! (assoc type-definition
                                :tp_name type-name-ptr
                                :tp_doc docstring-ptr
                                :tp_new tp_new
                                :tp_dealloc tp_dealloc
                                :tp_getattr tp_getattr
                                :tp_setattr tp_setattr))
          (incref-wrap-pyobject new-type))
        (throw (ex-info (format "Type failed to register: %d" type-ready)
                        {}))))))

(defn- pybridge->bridge
  ^JVMBridge [^PyObject pybridge]
  (let [bridge-type (JVMBridgeType. (.getPointer pybridge))]
    (get-jvm-bridge (.jvm_handle bridge-type)
                    (.jvm_interpreter_handle bridge-type))))


(defn register-bridge-type!
  "Register the bridge type and return newly created type."
  [module & {:keys [type-name]
             :or {type-name "jvm_bridge"}}]
  (with-gil
    (register-type!
     module
     {:type-name type-name
      :docstring "Type used to create the jvmbridge python objects"
      :method-definitions [{:name "__dir__"
                            :doc "Custom jvm bridge  __dir__ method"
                            :function (reify CFunction$NoArgFunction
                                        (pyinvoke [this self]
                                          (try
                                            (-> (pybridge->bridge self)
                                                (.dir)
                                                (->py-list))
                                            (catch Throwable e
                                              (log-error ("error calling __dir__:" e ))
                                              (->py-list [])))))}]
      :tp_basicsize (.size (JVMBridgeType.))
      :tp_dealloc (reify CFunction$tp_dealloc
                    (pyinvoke [this self]
                      (try
                        (-> (pybridge->bridge self)
                            (unregister-bridge!))
                        (catch Throwable e
                          (log-error e)))
                      ((.tp_free (PyTypeObject. (.ob_type self))) (.getPointer self))))
      :tp_getattr (reify CFunction$tp_getattr
                    (pyinvoke [this self att-name]
                      (try
                        (-> (pybridge->bridge self)
                            (.getAttr att-name))
                        (catch Throwable e
                          (log-error (format "getattr: %s: %s" att-name e))
                          nil))))
      :tp_setattr (reify CFunction$tp_setattr
                    (pyinvoke [this self att-name att-value]
                      (try
                        (-> (pybridge->bridge self)
                            (.setAttr att-name att-value))
                        (catch Throwable e
                          (log-error (format "setattr: %s: %s" att-name e))
                          0))))})))


(defn wrap-var-writer
  "Returns an unregistered bridge"
  ^JVMBridge [writer-var]
  (with-gil
    (let [write-fn (-> (reify CFunction$KeyWordFunction
                         (pyinvoke [this self tuple-args kw-args]
                           (try
                             (let [tuple-data (when tuple-args
                                                (copy-to-jvm tuple-args))
                                   kw-data (when kw-args
                                             (copy-to-jvm kw-args))]
                               (.write ^Writer @writer-var (str (first tuple-data)))
                               (py-none))
                             (catch Throwable e
                               (println "ERROR!!")
                               (log-error e)
                               (py-none)))))
                       create-function
                       incref)
          interpreter (ensure-bound-interpreter)]
      (reify JVMBridge
        (getAttr [bridge att-name]
          (case att-name
            "write" write-fn))
        (dir [bridge]
          (into-array String ["write"]))
        (setAttr [bridge att-name att-val]
          (throw (ex-info "Unsupported" {})))
        (interpreter [bridge] interpreter)
        (wrappedObject [bridge] writer-var)))))


(defn expose-bridge-to-python!
  "Create a python object for this bridge."
  [^JVMBridge bridge libpython-module]
  (with-interpreter (.interpreter bridge)
    (let [^PyObject bridge-type-ptr (get-attr libpython-module "jvm_bridge_type")
          _ (when-not bridge-type-ptr
              (throw (ex-info "Failed to find bridge type" {})))
          bridge-type (PyTypeObject. (.getPointer bridge-type-ptr))
          ^PyObject new-py-obj (libpy/_PyObject_New bridge-type)
          pybridge (JVMBridgeType. (.getPointer new-py-obj))]
      (set! (.jvm_interpreter_handle pybridge) (get-object-handle (.interpreter bridge)))
      (set! (.jvm_handle pybridge) (get-object-handle (.wrappedObject bridge)))
      (.write pybridge)
      (register-bridge! bridge pybridge)
      (wrap-pyobject pybridge))))


(defn get-or-create-var-writer
  [writer-var module]
  (if-let [existing-writer (find-jvm-bridge-entry (get-object-handle writer-var)
                                                  (ensure-interpreter))]
    (:pyobject existing-writer)
    (with-gil nil
      (-> (wrap-var-writer writer-var)
          (expose-bridge-to-python! module)))))


(defn setup-std-writer
  [writer-var libpy-module sys-mod-attname]
  (with-gil
    (let [sys-module (py-import-module "sys")
          std-out-writer (get-or-create-var-writer writer-var libpy-module)]
      (set-attr sys-module sys-mod-attname std-out-writer)
      :ok)))
