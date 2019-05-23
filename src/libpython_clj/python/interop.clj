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
              find-jvm-bridge
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
                     copy-to-jvm copy-to-python
                     ->py-dict
                     set-attr
                     get-attr
                     ->py-string
                     ->py-list]])
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
          (libpy/PyErr_SetString (libpy/PyExc_Exception)
                                 (format "%s:%s" e (with-out-str
                                                     (st/print-stack-trace e))))
          nil)))))


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
    (let [callback (if (or (instance? CFunction$KeyWordFunction fn-obj)
                           (instance? CFunction$TupleFunction fn-obj))
                     fn-obj
                     (wrap-clojure-fn fn-obj))
          tuple-args? (instance? CFunction$TupleFunction callback)
          meth-flags (long (if tuple-args?
                             @libpy/METH_VARARGS
                             @libpy/METH_KEYWORDS))
          current-interpreter (ensure-bound-interpreter)
          meth-def (PyMethodDef.)
          name-ptr (jna/string->ptr method-name)
          doc-ptr (jna/string->ptr documentation)
          py-self (or py-self (copy-to-python {}))]
      (set! (.ml_name meth-def) name-ptr)
      (set! (.ml_meth meth-def) (CallbackReference/getFunctionPointer callback))
      (set! (.ml_flags meth-def) (int meth-flags))
      (set! (.ml_doc meth-def) doc-ptr)
      (pyinterp/conj-forever! meth-def)
      (wrap-pyobject (libpy/PyCFunction_New meth-def py-self)))))


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
  [modname & {:keys [package docstring dont-import? unregistered?]}]
  (with-gil nil
    (let [new-module (wrap-pyobject (libpy/PyModule_New modname))]
      (when docstring
        (libpy/PyModule_SetDocString new-module docstring))
      (when package
        (set-attr new-module "__package__" (->py-string package)))
      (when-not unregistered?
        (let [sys-module (py-import-module "sys")
              sys-modules (get-attr sys-module "modules")]
          (libpy/PyDict_SetItemString sys-modules modname new-module)))
      new-module)))


(def fieldOffsetMethod
  (memoize
   (fn []
     (doto (.getDeclaredMethod Structure "fieldOffset" (into-array Class [String]))
       (.setAccessible true)))))


(defn offsetof
  [structure-instance fieldname]
  (.invoke ^Method (fieldOffsetMethod)
           structure-instance
           (into-array Object [(str fieldname)])))



(defn register-bridge-type!
  "Register the bridge type and return newly created type."
  [module & {:keys [type-name]
             :or {type-name "jvm_bridge"}}]
  (with-gil
    (let [interpreter (ensure-bound-interpreter)
          new-type (PyTypeObject.)
          docstring (jna/string->ptr "Type used to create the jvmbridge python objects")
          module-name (-> (get-attr module "__name__")
                          (copy-to-jvm))
          type-qualified-name (str module-name "." type-name)
          type-name-ptr (jna/string->ptr type-qualified-name)
          bridge-item (JVMBridgeType.)
          method-def-ary (-> (PyMethodDef.)
                             (.toArray 2))
          ^PyMethodDef first-method-def (aget method-def-ary 0)
          dir-method-name (jna/string->ptr "__dir__")
          dir-method-doc (jna/string->ptr "Custom __dir__ method")
          fn-callback (reify CFunction$NoArgFunction
                        (pyinvoke [this self]
                          (try
                            (let [bridge-type (JVMBridgeType. (.getPointer self))
                                  bridge-obj (get-jvm-bridge (.jvm_handle bridge-type)
                                                             (.jvm_interpreter_handle bridge-type))]
                              (->py-list (.dir bridge-obj)))
                            (catch Throwable e
                              (log-error ("error calling __dir__:" e ))
                              (->py-list [])))))]
      (set! (.ml_name first-method-def) dir-method-name)
      (set! (.ml_meth first-method-def) (CallbackReference/getFunctionPointer fn-callback))
      (set! (.ml_flags first-method-def) @libpy/METH_NOARGS)
      (set! (.ml_doc first-method-def) dir-method-doc)
      (.write first-method-def)
      ;;Some of the methods we have to do ourselves:
      (set! (.tp_name new-type) type-name-ptr)
      (set! (.tp_doc new-type) docstring)
      (set! (.tp_basicsize new-type) (.size bridge-item))
      (set! (.tp_flags new-type) (bit-or @libpy/Py_TPFLAGS_DEFAULT
                                         @libpy/Py_TPFLAGS_BASETYPE))
      (set! (.tp_new new-type) (reify CFunction$tp_new
                                 (pyinvoke [this self varargs kw_args]
                                   (libpy/PyType_GenericNew self varargs kw_args))))
      (set! (.tp_dealloc new-type) (reify CFunction$tp_dealloc
                                     (pyinvoke [this self]
                                       (try
                                         (let [bridge-type (JVMBridgeType. (.getPointer self))
                                               bridge-obj (get-jvm-bridge (.jvm_handle bridge-type)
                                                                          (.jvm_interpreter_handle bridge-type))]
                                           (unregister-bridge! bridge-obj))
                                         (catch Throwable e
                                           (log-error e)))
                                       ((.tp_free (PyTypeObject. (.ob_type self))) (.getPointer self)))))
      (set! (.tp_getattr new-type) (reify CFunction$tp_getattr
                                     (pyinvoke [this self att-name]
                                       (try
                                         (let [bridge-type (JVMBridgeType. (.getPointer self))
                                               bridge-obj (get-jvm-bridge (.jvm_handle bridge-type)
                                                                          (.jvm_interpreter_handle bridge-type))]
                                           (.getAttr bridge-obj att-name))
                                         (catch Throwable e
                                           (log-error (format "getattr: %s: %s" att-name e))
                                           nil)))))
      (set! (.tp_setattr new-type) (reify CFunction$tp_setattr
                                     (pyinvoke [this self att-name att-value]
                                       (try
                                         (let [bridge-type (JVMBridgeType. (.getPointer self))
                                               bridge-obj (get-jvm-bridge (.jvm_handle bridge-type)
                                                                          (.jvm_interpreter_handle bridge-type))]
                                           (.setAttr bridge-obj att-name att-value)
                                           1)
                                         (catch Throwable e
                                           (log-error (format "setattr: %s: %s" att-name e))
                                           0)))))
      (set! (.tp_methods new-type) (PyMethodDef$ByReference. (.getPointer first-method-def)))
      (let [type-ready (libpy/PyType_Ready new-type)]
        (if (>= 0 type-ready)
          (do
            (libpy/Py_IncRef new-type)
            (.read new-type)
            (libpy/PyModule_AddObject module (str type-name "_type") new-type)
            (conj-forever! [new-type method-def-ary
                            fn-callback
                            dir-method-name
                            dir-method-doc])
            new-type)
          (throw (ex-info (format "Type failed to register: %d" type-ready)
                          {})))))))


(defn wrap-var-writer
  "Returns an unregistered bridge"
  ^JVMBridge [writer-var]
  (with-gil
    (let [write-fn (create-function (fn [msg & args]
                                      (.write ^Writer @writer-var (str msg))
                                      nil))
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
        (wrappedObject [bridge] writer-var)
        (close [bridge])))))


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
      pybridge)))


(defn get-or-create-var-writer
  [writer-var module]
  (if-let [existing-writer (find-jvm-bridge (get-object-handle writer-var) (ensure-interpreter))]
    (:pyobject existing-writer)
    (with-gil nil
      (-> (wrap-var-writer writer-var)
          (expose-bridge-to-python! module)))))


(defn setup-std-writer
  [writer-var libpy-module sys-mod-attname]
  (with-gil
    (let [sys-module (py-import-module "sys")
          std-out-writer (get-or-create-var-writer #'*out* libpy-module)]
      (set-attr sys-module sys-mod-attname std-out-writer)
      :ok)))
