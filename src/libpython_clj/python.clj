(ns libpython-clj.python
  (:require [libpython-clj.jna :as libpy]
            [libpython-clj.jna.base :as libpy-base]
            [tech.jna.base :as jna-base]
            [tech.jna :as jna]
            [tech.resource :as resource]
            [tech.resource.gc :as resource-gc]
            [tech.parallel.require :as parallel-req]
            [tech.v2.datatype :as dtype])
  (:import [tech.resource GCSoftReference]
           [com.sun.jna Pointer Structure]
           [com.sun.jna.ptr PointerByReference
            LongByReference IntByReference]
           [java.lang AutoCloseable]
           [java.nio.charset StandardCharsets]
           [java.lang.reflect Field Method]
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
           [tech.v2.datatype ObjectIter]
           [java.io Writer]))


(set! *warn-on-reflection* true)


(defonce taoensso-logger
  (future {:info  (parallel-req/require-resolve 'tech.jna.timbre-log/log-info)
           :warn (parallel-req/require-resolve 'tech.jna.timbre-log/log-warn)
           :error  (parallel-req/require-resolve 'tech.jna.timbre-log/log-error)
           }))


(defn log-level
  [level msg]
  (if-let [logger (try (get @taoensso-logger level)
                       (catch Throwable e nil))]
    (logger msg)
    (println (format "%s: %s" (name level) msg))))


(defn log-error
  [log-str]
  (log-level :error log-str))


(defn log-info
  [log-str]
  (log-level :info log-str))


(defn logthrow-error
  [log-str & [data]]
  (throw (ex-info log-str data)))


;;All interpreters share the same type symbol table as types are uniform
;;across initializations.  So given an unknown item, we can in constant time
;;get the type of that item if we have seen it before.
(defrecord Interpreter [thread-state*
                        type-symbol-table*
                        ;;Things like function pointers that cannot ever leave scope
                        forever*
                        ;;A two-way map of integer pyobj handle to java object wrapper
                        objects*])


(defonce ^:dynamic *interpreters* (atom {}))


(defn get-object-handle
  [interpreter]
  (System/identityHashCode interpreter))

(defn add-interpreter-handle!
  [interpreter]
  (swap! *interpreters* assoc
         (get-object-handle interpreter)
         interpreter))


(defn remove-interpreter-handle!
  [interpreter]
  (swap! *interpreters* dissoc
         (get-object-handle interpreter)))


(defn handle->interpreter
  [interpreter-handle]
  (if-let [retval (get @*interpreters* interpreter-handle)]
    retval
    (throw (ex-info "Failed to convert from handle to interpreter"
                    {}))))

(defn handle-or-interpreter->interpreter
  [hdl-or-interp]
  (if (number? hdl-or-interp)
    (handle->interpreter hdl-or-interp)
    hdl-or-interp))


(defonce ^:dynamic *main-interpreter* (atom nil))
(defonce ^:dynamic *current-thread-interpreter* nil)



(defn find-jvm-bridge
  ^JVMBridge [handle interpreter]
  (when-let [interpreter (handle-or-interpreter->interpreter interpreter)]
    (when-let [^JVMBridge bridge-object (get @(:objects* interpreter) handle)]
      bridge-object)))


(defn get-jvm-bridge
  ^JVMBridge [handle interpreter]
  (if-let [bridge-obj (find-jvm-bridge handle interpreter)]
    (:jvm-bridge bridge-obj)
    (throw (ex-info "Unable to find bridge for interpreter %s and handle %s"
                    interpreter handle))))


(defn register-bridge!
  [^JVMBridge bridge ^PyObject bridge-pyobject]
  (let [interpreter (.interpreter bridge)
        bridge-handle (get-object-handle (.wrappedObject bridge))]
    (when (find-jvm-bridge bridge-handle (.interpreter bridge))
      (throw (ex-info "already-registered?" {})))
    (swap! (:objects* interpreter) assoc
           bridge-handle
           {:jvm-bridge bridge
            :pyobject bridge-pyobject})
    :ok))


(defn unregister-bridge!
  [^JVMBridge bridge]
  (let [interpreter (.interpreter bridge)
        bridge-handle (get-object-handle (.wrappedObject bridge))]
    (swap! (:objects* interpreter) dissoc bridge-handle)
    :ok))


(def ^:dynamic *program-name* "")


(defn- finalize-global-interpreter!
  [thread-state-atom forever-atom obj-map-atom]
  (when-let [thread-state (first (swap-vals! thread-state-atom (constantly nil)))]
    (log-info "Destroying global python interpreter")
    (libpy/PyEval_RestoreThread thread-state)
    (let [finalize-val (long (libpy/Py_FinalizeEx))]
      (reset! forever-atom nil)
      (reset! obj-map-atom nil)
      (when-not (= 0 finalize-val)
        (log-error (format "Py_Finalize failure: %s"
                           finalize-val))))))


(defn initialize!
  [& [program-name]]
  (when-not @*main-interpreter*
    (log-info "Creating global python interpreter")
    (libpy/Py_InitializeEx 0)
    ;;Set program name
    (when-let [program-name (or program-name *program-name* "")]
      (resource/stack-resource-context
       (libpy/PySys_SetArgv 0 (-> program-name
                                  (jna/string->wide-ptr)))))
    (let [type-symbols (libpy/lookup-type-symbols)
          retval (->Interpreter (atom (libpy/PyEval_SaveThread))
                                (atom type-symbols)
                                (atom [])
                                (atom {}))
          thread-state-atom (:thread-state* retval)
          forever-atom (:forever* retval)
          objects-atom (:objects* retval)]
      (reset! *main-interpreter* retval)
      (add-interpreter-handle! retval)
      :ok)))


(defn unsafe-destroy-global-interpreter!
  []
  (when-let [main-interpreter (first (swap-vals! *main-interpreter* (constantly nil)))]
    (finalize-global-interpreter! (:thread-state* main-interpreter)
                                  (:forever* main-interpreter)
                                  (:objects* main-interpreter))
    (remove-interpreter-handle! main-interpreter)))


(defn- ensure-interpreter
  []
  (if-let [retval (or @*main-interpreter*
                      *current-thread-interpreter*)]
    retval
    (throw (ex-info "No interpreters found" {}))))



(defmacro with-gil
  [interpreter & body]
  `(let [interpreter# (or ~interpreter (ensure-interpreter))
         unbound?# (not *current-thread-interpreter*)]
     (if unbound?#
       (locking interpreter#
         (try
           (with-bindings {#'*current-thread-interpreter* interpreter#}
             (libpy/PyEval_RestoreThread @(:thread-state* interpreter#))
             ~@body)
           (finally
             (reset! (:thread-state* interpreter#)
                     (libpy/PyEval_SaveThread)))))
       (do
         ~@body))))



(defn wrap-pyobject
  "Wrap object such that when it is no longer accessible via the program decref is
  called."
  [pyobj]
  (let [interpreter (ensure-interpreter)
        pyobj-value (Pointer/nativeValue (jna/as-ptr pyobj))]
    (resource/track pyobj #(with-gil interpreter
                             (try
                               (libpy/Py_DecRef (Pointer. pyobj-value))
                               (catch Throwable e
                                 (log-error "Exception while releasing object: %s" e))))
                    [:gc])))


(defn incref-wrap-pyobject
  "Increment the object's refcount and then call wrap-pyobject."
  [pyobj]
  (with-gil nil
    (libpy/Py_IncRef pyobj)
    (wrap-pyobject pyobj)))


(defn py-true
  []
  (libpy/Py_True))


(defn py-false
  []
  (libpy/Py_False))


(defn py-none
  []
  (libpy/Py_None))


(defn py-not-implemented
  []
  (libpy/Py_NotImplemented))


(defn py-raw-type
  [pyobj]
  (with-gil nil
    (-> (libpy/PyObject_Type pyobj)
        wrap-pyobject)))


(defn py-type-keyword
  [pyobj]
  (with-gil nil
    (let [interpreter (ensure-interpreter)
          sym-table-atom (:type-symbol-table* interpreter)
          py-type (py-raw-type pyobj)
          py-type-addr (Pointer/nativeValue ^Pointer (jna/as-ptr py-type))
          sym-table
          (swap! sym-table-atom
                 (fn [sym-table]
                   (if-let [retval (get-in sym-table [py-type-addr :typename])]
                     sym-table
                     (assoc sym-table py-type-addr {:typename (libpy/get-type-name py-type)}))))]
      (get-in sym-table [py-type-addr :typename]))))


(defn new-size-t-by-reference
  []
  (if (instance? Long (jna/size-t 0))
    (LongByReference.)
    (IntByReference.)))


(defn size-by-ref-value
  ^long [byref]
  (long
   (if (instance? LongByReference byref)
     (.getValue ^LongByReference byref)
     (.getValue ^IntByReference byref))))


(defn py-string->string
  ^String [pyobj]
  (with-gil nil
    (when-not (= :str (py-type-keyword pyobj))
      (throw (ex-info (format "Object passed in is not a string: %s"
                              (py-type-keyword pyobj))
                      {})))
    (let [size-obj (new-size-t-by-reference)
          ^Pointer str-ptr (libpy/PyUnicode_AsUTF8AndSize pyobj size-obj)
          n-elems (size-by-ref-value size-obj)]
      (-> (.decode StandardCharsets/UTF_8 (.getByteBuffer str-ptr 0 n-elems))
          (.toString)))))


(defn pyobj->string
  ^String [pyobj]
  (with-gil nil
    (let [py-str (if (= :str (py-type-keyword pyobj))
                   pyobj
                   (-> (libpy/PyObject_Str pyobj)
                       wrap-pyobject))]
      (py-string->string py-str))))


(defn py-dir
  [pyobj]
  (with-gil nil
    (let [item-dir (libpy/PyObject_Dir pyobj)]
      (->> (range (libpy/PyObject_Length item-dir))
           (mapv (fn [idx]
                   (-> (libpy/PyObject_GetItem item-dir
                                               (libpy/PyLong_FromLong idx))
                       pyobj->string)))))))


(defn ->python
  [item]
  (libpy-base/->py-object-ptr item))


(declare ->py-tuple ->py-list
         ->py-dict ->py-string
         ->py-long ->py-float
         has-attr? get-attr)


(defn copy-to-python
  [item]
  (cond
    (integer? item)
    (->py-long item)
    (number? item)
    (->py-float item)
    (string? item)
    (->py-string item)
    (keyword? item)
    (copy-to-python (name item))
    (map? item)
    (->py-dict item)
    (seq item)
    (if (and (< (count item) 4)
             (vector? item))
      (->py-tuple item)
      (->py-list item))
    :else
    (libpy-base/->py-object-ptr item)))


(declare copy-to-jvm)


(defn python->jvm-copy-hashmap
  [pyobj & [map-items]]
  (with-gil nil
    (when-not (= 1 (libpy/PyMapping_Check pyobj))
      (throw (ex-info (format "Object does not implement the mapping protocol: %s"
                              (py-type-keyword pyobj)))))
    (->> (or map-items
             (-> (libpy/PyMapping_Items pyobj))
             wrap-pyobject)
         copy-to-jvm
         (into {}))))


(defn python->jvm-copy-persistent-vector
  [pyobj]
  (with-gil nil
    (when-not (= 1 (libpy/PySequence_Check pyobj))
      (throw (ex-info (format "Object does not implement sequence protocol: %s"
                              (py-type-keyword pyobj)))))

    (->> (range (libpy/PySequence_Length pyobj))
         (mapv (fn [idx]
                 (-> (libpy/PySequence_GetItem pyobj idx)
                     wrap-pyobject
                     copy-to-jvm))))))


(defn python->jvm-copy-iterable
  "Create an iterable that auto-copies what it iterates completely into the jvm."
  [pyobj]
  (with-gil nil
    (when-not (= 1 (has-attr? pyobj "__iter__"))
      (throw (ex-info (format "object is not iterable: %s"
                              (py-type-keyword pyobj))
                      {})))
    (let [iter-callable (get-attr pyobj "__iter__")
          interpreter *current-thread-interpreter*]
      (reify Iterable
        (iterator [item]
          (let [py-iter (with-gil interpreter
                          (-> (libpy/PyObject_CallObject iter-callable nil)
                              wrap-pyobject))
                next-fn (fn [last-item]
                          (with-gil interpreter
                            (when-let [next-obj (libpy/PyIter_Next py-iter)]
                              (-> next-obj
                                  (wrap-pyobject)
                                  (copy-to-jvm)))))
                cur-item-store (atom (next-fn nil))]
            (reify ObjectIter
              (hasNext [obj-iter] (boolean @cur-item-store))
              (next [obj-iter]
                (locking py-iter
                  (let [cur-item (.current obj-iter)]
                    (swap! cur-item-store next-fn)
                    cur-item)))
              (current [obj-iter]
                @cur-item-store))))))))


(defn copy-to-jvm
  [pyobj]
  (with-gil nil
    (case (py-type-keyword pyobj)
      :int
      (libpy/PyLong_AsLongLong pyobj)
      :float
      (libpy/PyFloat_AsDouble pyobj)
      :str
      (py-string->string pyobj)
      :none-type
      nil
      (cond
        ;;Things could implement mapping and sequence logically so mapping
        ;;takes precedence
        (= 1 (libpy/PyMapping_Check pyobj))
        (if-let [map-items (-> (libpy/PyMapping_Items pyobj)
                               wrap-pyobject)]
          (python->jvm-copy-hashmap pyobj map-items)
          (do
            ;;Ignore error.  The mapping check isn't thorough enough to work.
            (libpy/PyErr_Clear)
            (python->jvm-copy-persistent-vector pyobj)))
        ;;Sequences become persistent vectors
        (= 1 (libpy/PySequence_Check pyobj))
        (python->jvm-copy-persistent-vector)
        (= 1 (has-attr? pyobj "__iter__"))
        (python->jvm-copy-iterable pyobj)
        :else
        {:type (py-type-keyword pyobj)
         :value pyobj}))))


(defn ->py-long
  [item]
  (with-gil nil
    (wrap-pyobject
     (libpy/PyLong_FromLongLong (long item)))))


(defn ->py-float
  [item]
  (with-gil nil
    (wrap-pyobject
     (libpy/PyFloat_FromDouble (double item)))))


(defn ->py-string
  [item]
  (with-gil nil
    (let [byte-data (.getBytes ^String item StandardCharsets/UTF_16)]
      (wrap-pyobject
       (libpy/PyUnicode_Decode byte-data (dtype/ecount byte-data)
                               "UTF-16" "strict")))))


(defn ->py-dict
  [item]
  (with-gil nil
    (let [dict (libpy/PyDict_New)]
      (doseq [[k v] item]
        (libpy/PyDict_SetItem dict (copy-to-python k)
                              (copy-to-python v)))
      (wrap-pyobject
       dict))))


(defn ->py-list
  [item-seq]
  (with-gil nil
    (let [retval (libpy/PyList_New (count item-seq))]
      (->> item-seq
           (map-indexed (fn [idx item]
                          (libpy/PyList_SetItem
                           retval
                           idx
                           (let [new-val (copy-to-python item)]
                             (libpy/Py_IncRef new-val)
                             new-val))))
           dorun)
      (wrap-pyobject retval))))


(defn ->py-tuple
  [item-seq]
  (with-gil nil
    (let [n-items (count item-seq)
          new-tuple (libpy/PyTuple_New n-items)]
      (->> item-seq
           (map-indexed (fn [idx item]
                          (libpy/PyTuple_SetItem
                           new-tuple
                           idx
                           (let [new-val (copy-to-python item)]
                             (libpy/Py_IncRef new-val)
                             new-val))))
           dorun)
      (wrap-pyobject new-tuple))))


(defn- stringable?
  [item]
  (or (keyword? item)
      (string? item)
      (symbol? item)))


(defn- stringable
  ^String [item]
  (if (string? item)
    item
    (name item)))


(defn has-attr?
  [pyobj attr-name]
  (with-gil nil
    (if (stringable? attr-name)
      (libpy/PyObject_HasAttrString pyobj (stringable attr-name))
      (libpy/PyObject_HasAttrString pyobj (copy-to-python attr-name)))))


(defn get-attr
  [pyobj attr-name]
  (with-gil nil
    (if (stringable? attr-name)
      (wrap-pyobject (libpy/PyObject_GetAttrString pyobj (stringable attr-name)))
      (wrap-pyobject (libpy/PyObject_GetAttr pyobj (copy-to-python attr-name))))))


(defn set-attr
  [pyobj attr-name attr-value]
  (with-gil nil
    (if (stringable? attr-name)
      (libpy/PyObject_SetAttrString pyobj (stringable attr-name) attr-value)
      (libpy/PyObject_SetAttr pyobj attr-name attr-value))
    pyobj))


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
          (println "no idea how to handle this" e)
          (libpy/Py_None))))))


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
  (with-gil nil
    (let [callback (if (or (instance? CFunction$KeyWordFunction fn-obj)
                           (instance? CFunction$TupleFunction fn-obj))
                     fn-obj
                     (wrap-clojure-fn fn-obj))
          tuple-args? (instance? CFunction$TupleFunction callback)
          meth-flags (long (if tuple-args?
                             @libpy/METH_VARARGS
                             @libpy/METH_KEYWORDS))
          current-interpreter *current-thread-interpreter*
          forever-atom (:forever* current-interpreter)
          meth-def (PyMethodDef.)
          name-ptr (jna/string->ptr method-name)
          doc-ptr (jna/string->ptr documentation)
          py-self (or py-self (copy-to-python {}))]
      (set! (.ml_name meth-def) name-ptr)
      (set! (.ml_meth meth-def) callback)
      (set! (.ml_flags meth-def) (int meth-flags))
      (set! (.ml_doc meth-def) doc-ptr)
      ;;The method definition can neither change nor go out of scope.
      (swap! forever-atom conj meth-def)
      (wrap-pyobject (libpy/PyCFunction_New meth-def py-self)))))


(extend-type Object
  libpy-base/PToPyObjectPtr
  (->py-object-ptr [item]
    (cond
      :else
      (jna/as-ptr item))))


(defn py-import-module
  [modname]
  (with-gil nil
    (wrap-pyobject
     (libpy/PyImport_ImportModule modname))))


(defn py-add-module
  [modname]
  (with-gil nil
    (libpy/PyImport_AddModule modname)))


(defn obj-has-item?
  [elem elem-name]
  (with-gil nil
    (if (stringable? elem-name)
      (libpy/PyMapping_HasKeyString elem (stringable elem-name))
      (libpy/PyMapping_HasKey elem elem-name))))


(defn obj-get-item
  [elem elem-name]
  (with-gil nil
    (if (stringable? elem-name)
      (libpy/PyMapping_GetItemString elem (stringable elem-name))
      (libpy/PyObject_GetItem elem elem-name))))


(defn obj-set-item
  [elem elem-name elem-value]
  (with-gil nil
    (if (stringable? elem-name)
      (libpy/PyMapping_SetItemString elem (stringable elem-name) elem-value)
      (libpy/PyObject_SetItem elem elem-name elem-value))
    elem))


(defn run-simple-string
  "Run a simple string returning boolean 1 or 0

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
  (with-gil nil
    (libpy/PyRun_SimpleString program)))


(defn run-string
  "If you don't know what you are doing, this will sink you."
  [^String program & {:keys [globals locals]}]
  (with-gil nil
    (let [globals (or globals (->py-dict {}))
          locals (or locals (->py-dict {}))]
      {:result
       (wrap-pyobject
        (libpy/PyRun_String (str program) :py-file-input globals locals))
       :globals globals
       :locals locals})))


(defn test-cpp-example
  []
  (initialize!)
  (with-gil nil
    (let [python-script "result = multiplicand * multiplier\n"
          local-dict (->py-dict {"multiplicand" 2
                                 "multiplier" 5})]
      (run-string python-script :locals local-dict))))


(defn create-module
  [modname & {:keys [package docstring dont-import?]}]
  (with-gil nil
    (let [new-module (wrap-pyobject (libpy/PyModule_New modname))]
      (when docstring
        (libpy/PyModule_SetDocString new-module docstring))
      (when package
        (set-attr new-module "__package__" (->py-string package)))
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
  [module]
  (with-gil nil
    (let [interpreter *current-thread-interpreter*
          new-type (PyTypeObject.)
          docstring (jna/string->ptr "Type used to create the jvmbridge python objects")
          module-name (-> (get-attr module "__name__")
                          (copy-to-jvm))
          type-name "jvm_bridge"
          type-qualified-name (str module-name "." type-name)
          type-name-ptr (jna/string->ptr type-qualified-name)
          bridge-item (JVMBridgeType.)
          method-def-ary (-> (PyMethodDef.)
                             (.toArray 2))
          ^PyMethodDef first-method-def (aget method-def-ary 0)
          dir-method-name (jna/string->ptr "__dir__")
          dir-method-doc (jna/string->ptr "Custom __dir__ method")]
      (set! (.ml_name first-method-def) dir-method-name)
      (set! (.ml_meth first-method-def) (reify CFunction$NoArgFunction
                                          (pyinvoke [this self]
                                            (try
                                              (let [bridge-type (JVMBridgeType. (.getPointer self))
                                                    bridge-obj (get-jvm-bridge (.jvm_handle bridge-type)
                                                                               (.jvm_interpreter_handle bridge-type))]
                                                (->py-list (.dir bridge-obj)))
                                              (catch Throwable e
                                                (log-error ("error calling __dir__:" e ))
                                                (->py-list []))))))
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
                                       (let [bridge-type (JVMBridgeType. (.getPointer self))
                                             bridge-obj (get-jvm-bridge (.jvm_handle bridge-type)
                                                                        (.jvm_interpreter_handle bridge-type))
                                             self-type (PyTypeObject. (.ob_type self))]
                                         (try
                                           (unregister-bridge! bridge-obj)
                                           (catch Throwable e
                                             (log-error e)))
                                         ((.tp_free self-type) (.getPointer self))))))
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
            ;;type-ready changes the type memory backing store
            (.read new-type)
            ;;update the refcount to 1
            (set! (.ob_refcnt new-type) 1)
            ;;write that to the backing store
            (.write new-type)
            (libpy/PyModule_AddObject module (str type-name "_type") new-type)
            (swap! (:forever* interpreter) conj [new-type method-def-ary])
            new-type)
          (throw (ex-info (format "Type failed to register: %d" type-ready)
                          {})))))))


(defn wrap-var-writer
  "Returns an unregistered bridge"
  ^JVMBridge [writer-var]
  (with-gil nil
    (let [write-fn (create-function (fn [msg & args]
                                      (.write ^Writer @writer-var (str msg))
                                      nil))
          interpreter (ensure-interpreter)]
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
  (with-gil (.interpreter bridge)
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
  (with-gil nil
    (let [sys-module (py-import-module "sys")
          std-out-writer (get-or-create-var-writer #'*out* libpy-module)]
      (set-attr sys-module sys-mod-attname std-out-writer)
      :ok)))
