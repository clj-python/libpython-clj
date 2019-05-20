(ns libpython-clj.python
  (:require [libpython-clj.jna :as libpy]
            [libpython-clj.jna.base :as libpy-base]
            [tech.jna.base :as jna-base]
            [tech.jna :as jna]
            [tech.resource :as resource]
            [tech.resource.gc :as resource-gc]
            [tech.parallel.require :as parallel-req]
            [clojure.core.async :as async]
            [tech.v2.datatype :as dtype])
  (:import [tech.resource GCSoftReference]
           [com.sun.jna Pointer]
           [com.sun.jna.ptr PointerByReference
            LongByReference IntByReference]
           [java.lang AutoCloseable]
           [java.nio.charset StandardCharsets]
           [libpython_clj.jna
            CFunction$KeyWordFunction
            CFunction$TupleFunction
            PyMethodDef
            ]
           [tech.v2.datatype ObjectIter]))


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
                        ;;A two-way map of integer pyobj handle to java object.
                        objects*])



(defonce ^:dynamic *main-interpreter* (atom nil))
(defonce ^:dynamic *current-thread-interpreter* nil)


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
                                (atom {:handle->obj {}
                                       :obj->handle {}}))
          thread-state-atom (:thread-state* retval)
          forever-atom (:forever* retval)
          objects-atom (:objects* retval)]
      (reset! *main-interpreter* (resource/track
                                  retval
                                  #(finalize-global-interpreter! thread-state-atom
                                                                 forever-atom
                                                                 objects-atom)
                                  [:gc]))
      :ok)))


(defn unsafe-destroy-global-interpreter!
  []
  (when-let [main-interpreter (first (swap-vals! *main-interpreter* (constantly nil)))]
    (finalize-global-interpreter! (:thread-state* main-interpreter)
                                  (:forever* main-interpreter)
                                  (:objects* main-interpreter))))


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
                             (libpy/Py_DecRef (Pointer. pyobj-value)))
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
          py-type-addr (Pointer/nativeValue ^Pointer py-type)
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
  [pyobj]
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
        (throw (ex-info (format "Unable to map python object into jvm at this time: %s"
                         (py-type-keyword pyobj)) {}))))))


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
