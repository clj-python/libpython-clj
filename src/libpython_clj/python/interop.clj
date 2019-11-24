(ns libpython-clj.python.interop
  "The messy details of actual embedding python in the jvm, aside from interpreter
  state, go here.  Don't expect a pleasant ride.  If you want to create a new python
  type or function, something that requires knowledge of the C structures behind
  everything that knowledge should be encoded in this file."
  (:require [libpython-clj.jna :as libpy]
            [libpython-clj.jna.base :as libpy-base]
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
            [clojure.stacktrace :as st]
            [tech.jna :as jna]
            ;;need memset
            [tech.v2.datatype.nio-buffer :as nio-buf]
            [tech.resource :as resource]
            [libpython-clj.python.object
             :refer [wrap-pyobject incref-wrap-pyobject
                     incref
                     ->jvm ->python
                     ->py-dict
                     get-attr
                     py-none
                     ->py-string
                     ->py-list
                     ->py-tuple
                     ->py-fn
                     apply-method-def-data!]
             :as pyobject]
            [libpython-clj.python.protocols
             :refer [python-type]
             :as py-proto])
  (:import [java.lang.reflect Field Method]
           [com.sun.jna Pointer Structure CallbackReference]
           [libpython_clj.jna
            CFunction$KeyWordFunction
            CFunction$TupleFunction
            CFunction$NoArgFunction
            CFunction$tp_new
            CFunction$tp_free
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


(def libpython-clj-module-name
  "Module name of the libpython-clj python model.  Used to find binding-level objects
  such as the type used for actual jvm bridging objects."
  "libpython_clj")


(defn import-module
  [modname]
  (with-gil
    (-> (libpy/PyImport_ImportModule modname)
        wrap-pyobject)))


(defn add-module
  [modname]
  (with-gil
    (-> (libpy/PyImport_AddModule modname)
        (incref-wrap-pyobject))))


(defn module-dict
  [module]
  (with-gil
    (-> (libpy/PyModule_GetDict module)
        incref-wrap-pyobject)))


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
    (let [main-mod (libpy/PyImport_AddModule "__main__")
          globals (or globals (incref-wrap-pyobject
                               (libpy/PyModule_GetDict main-mod)))
          locals (or locals globals)
          retval (-> (libpy/PyRun_String (str program)
                                         :py-file-input
                                         globals locals)
                     wrap-pyobject)]
      {:globals globals
       :locals locals})))


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


(def type-obj-size (-> (PyTypeObject.)
                       (.size)))


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
                  tp_iter ;;may be nil
                  tp_iternext ;;may be nil
                  ]
           :as type-definition}]
  (resource/stack-resource-context
   (when (or (not type-name)
             (= 0 (count type-name)))
     (throw (ex-info "Cannot create unnamed type." {})))
   (let [tp_new (or tp_new
                    (reify CFunction$tp_new
                      (pyinvoke [this self varargs kw_args]
                        (libpy/PyType_GenericNew self varargs kw_args))))
         module-name (get-attr module "__name__")
         ;;These get leaked.  Really, types are global objects that cannot be released.
         ;;Until we can destroy interpreters, it isn't worth the effort to track the
         ;;type and memory related to the type.
         docstring-ptr (jna/string->ptr-untracked docstring)
         type-name-ptr (jna/string->ptr-untracked (str module-name "." type-name))
         tp_flags (long (or tp_flags
                            (bit-or @libpy/Py_TPFLAGS_DEFAULT
                                    @libpy/Py_TPFLAGS_BASETYPE)))
         ;;We allocate our memory manually here else the system will gc the
         ;;type object memory when the type goes out of scope.
         new-mem (jna/malloc-untracked type-obj-size)
         _ (nio-buf/memset new-mem 0 type-obj-size)
         new-type (PyTypeObject. new-mem)]
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
     (when tp_iter
       (set! (.tp_iter new-type) tp_iter))
     (when tp_iternext
       (set! (.tp_iternext new-type) tp_iternext))
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
           (libpy/Py_IncRef new-type)
           new-type)
         (throw (ex-info (format "Type failed to register: %d" type-ready)
                         {})))))))

(defn pybridge->bridge
  ^JVMBridge [^Pointer pybridge]
  (let [bridge-type (JVMBridgeType. pybridge)]
    (get-jvm-bridge (.jvm_handle bridge-type)
                    (.jvm_interpreter_handle bridge-type))))


(defn register-bridge-type!
  "Register the bridge type and return newly created type."
  [& {:keys [type-name module]
      :or {type-name "jvm_bridge"}}]
  (with-gil
    (let [module (or module (add-module
                             libpython-clj-module-name))]
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
                                                (log-error "error calling __dir__:" e )
                                                (->py-list [])))))}]
        :tp_basicsize (.size (JVMBridgeType.))
        :tp_dealloc (reify CFunction$tp_dealloc
                      (pyinvoke [this self]
                        (try
                          (let [bridge (pybridge->bridge self)]
                            (try (.close bridge)
                                 (catch Throwable e
                                   (log-error (format "%s:%s"
                                               e
                                               (with-out-str
                                                 (st/print-stack-trace e))))))
                            (unregister-bridge! bridge))
                          (catch Throwable e
                            (log-error e)))
                        (try
                          (let [^CFunction$tp_free free-func
                                (.tp_free (PyTypeObject.
                                           (libpy/PyObject_Type self)))]
                            (when free-func
                              (.pyinvoke free-func self)))
                          (catch Throwable e
                            (log-error (format "%s:%s"
                                               e
                                               (with-out-str
                                                 (st/print-stack-trace e))))))))
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
                            0))))
        :tp_iter (reify CFunction$NoArgFunction
                   (pyinvoke [this self]
                     (let [attr
                           (-> (pybridge->bridge self)
                               (.getAttr "__iter__"))]
                       (py-proto/call attr))))

        :tp_iternext (reify CFunction$NoArgFunction
                       (pyinvoke [this self]
                         (when-let [next
                                    (-> (pybridge->bridge self)
                                        (.nextFn))]
                           (next))))}))))


(defn expose-bridge-to-python!
  "Create a python object for this bridge."
  [^JVMBridge bridge & [libpython-module]]
  (with-interpreter (.interpreter bridge)
    (let [libpython-module (or libpython-module
                               (add-module libpython-clj-module-name))
          ^Pointer bridge-type-ptr (get-attr libpython-module
                                             "jvm_bridge_type")
          _ (when-not bridge-type-ptr
              (throw (ex-info "Failed to find bridge type" {})))
          bridge-type (PyTypeObject. bridge-type-ptr)
          ^Pointer new-py-obj (libpy/_PyObject_New bridge-type)
          pybridge (JVMBridgeType. new-py-obj)]
      (set! (.jvm_interpreter_handle pybridge) (get-object-handle
                                                (.interpreter bridge)))
      (set! (.jvm_handle pybridge) (get-object-handle (.wrappedObject bridge)))
      (.write pybridge)
      (register-bridge! bridge pybridge)
      (-> (.getPointer pybridge)
          wrap-pyobject))))


(defn create-bridge-from-att-map
  [src-item att-map & {:keys [next-fn]}]
  (with-gil
    (let [interpreter (ensure-bound-interpreter)
          dir-data (->> (keys att-map)
                        sort
                        (into-array String))
          bridge
          (reify
            JVMBridge
            (getAttr [bridge att-name]
              (if-let [retval (get att-map att-name)]
                (incref retval)
                (libpy/Py_None)))
            (setAttr [bridge att-name att-value]
              (throw (ex-info "Cannot set attributes" {})))
            (dir [bridge] dir-data)
            (interpreter [bridge] interpreter)
            (wrappedObject [bridge] src-item)
            (nextFn [bridge] next-fn)
            libpy-base/PToPyObjectPtr
            (->py-object-ptr [item]
              (with-gil
                (if-let [existing-bridge (find-jvm-bridge-entry
                                          (get-object-handle src-item)
                                          (ensure-bound-interpreter))]
                  (:pyobject existing-bridge)
                  (expose-bridge-to-python! item))))
            py-proto/PCopyToJVM
            (->jvm [item options] src-item)
            py-proto/PBridgeToJVM
            (as-jvm [item options] src-item))]
      (expose-bridge-to-python! bridge)
      (libpy-base/->py-object-ptr bridge))))


(defmethod py-proto/pyobject->jvm :jvm-bridge
  [pyobj]
  (-> (pybridge->bridge pyobj)
      (.wrappedObject)))


(defmethod py-proto/pyobject-as-jvm :jvm-bridge
  [pyobj]
  (-> (pybridge->bridge pyobj)
      (.wrappedObject)))


(defn create-var-writer
  "Returns an unregistered bridge"
  ^Pointer [writer-var varname]
  (with-gil
    (create-bridge-from-att-map
     writer-var
     {"write" (->python (fn [& args]
                          (.write ^Writer @writer-var (str (first args)))))
      "flush" (->python (fn [& args]))
      "isatty" (->python (fn [& args]
                           (libpy/Py_False)))
      })))


(defn get-or-create-var-writer
  [writer-var varname]
  (if-let [existing-writer (find-jvm-bridge-entry (get-object-handle writer-var)
                                                  (ensure-interpreter))]
    (:pyobject existing-writer)
    (create-var-writer writer-var varname)))


(defn setup-std-writer
  [writer-var sys-mod-attname]
  (with-gil
    (let [sys-module (import-module "sys")
          std-out-writer (get-or-create-var-writer writer-var sys-mod-attname)]
      (py-proto/set-attr! sys-module sys-mod-attname std-out-writer)
      :ok)))
