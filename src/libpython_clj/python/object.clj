(ns libpython-clj.python.object
  "Base support for python objects and python->jvm interop.  At this level (without
  interop), we can only support the copying protocols; we can't do bridging.  Still,
  copying gets you quite far and you can, for instance, call python functions and get
  the attribute map from a python object.

  Protocol functions implemented:
  python-type
  ->python
  ->jvm
  dir
  has-attr?
  attr
  callable?
  has-item?
  item
  set-item!
  do-call-fn
  len

  Results of these, when they return python pointers, return the raw,unwrapped pointers.
  Callers at this level are sitting just close enough to the actual libpy calls to still
  get pointers back *but* they don't have to manage the gil."
  (:require [libpython-clj.python.interpreter
             :refer [with-gil
                     with-interpreter
                     ensure-interpreter
                     ensure-bound-interpreter
                     check-error-throw]
             :as pyinterp]
            [libpython-clj.python.protocols
             :refer [pyobject->jvm
                     python-type]
             :as py-proto]
            [libpython-clj.python.logging
             :refer [log-error log-warn log-info]]
            [libpython-clj.jna.base :as libpy-base]
            [libpython-clj.jna :as libpy]
            [clojure.stacktrace :as st]
            [tech.jna :as jna]
            [tech.resource :as resource]
            [tech.v2.datatype :as dtype]
            [tech.v2.datatype.protocols :as dtype-proto]
            [tech.v2.datatype.casting :as casting]
            [tech.v2.tensor])
  (:import [com.sun.jna Pointer CallbackReference]
           [libpython_clj.jna
            PyObject
            CFunction$KeyWordFunction
            CFunction$TupleFunction
            CFunction$NoArgFunction
            PyMethodDef]
           [java.nio.charset StandardCharsets]
           [tech.v2.datatype ObjectIter]
           [tech.v2.datatype.typed_buffer TypedBuffer]
           [tech.v2.tensor.impl Tensor]
           [java.util RandomAccess Map Set Map$Entry]
           [clojure.lang Symbol Keyword
            IPersistentMap
            IPersistentVector
            IPersistentSet]))


(set! *warn-on-reflection* true)



(extend-protocol py-proto/PCopyToJVM
  Pointer
  (->jvm [item options]
    (pyobject->jvm item))
  PyObject
  (->jvm [item options]
    (pyobject->jvm (.getPointer item))))


(extend-protocol py-proto/PBridgeToPython
  Pointer
  (as-python [item options] item)
  PyObject
  (as-python [item options] (.getPointer item)))


(extend-protocol py-proto/PCopyToPython
  Pointer
  (->python [item options] item)
  PyObject
  (->python [item options] (.getPointer item)))


(defn ->jvm
  "Copy an object into the jvm (if it wasn't there already.)"
  [item & [options]]
  (py-proto/->jvm item options))


(def ^:dynamic *object-reference-logging* false)


(defn wrap-pyobject
  "Wrap object such that when it is no longer accessible via the program decref is
  called. Used for new references.  This is some of the meat of the issue, however,
  in that getting the two system's garbage collectors to play nice is kind
  of tough."
  [pyobj]
  (check-error-throw)
  (when pyobj
    (let [interpreter (ensure-bound-interpreter)
          pyobj-value (Pointer/nativeValue (jna/as-ptr pyobj))
          py-type-name (name (python-type pyobj))]
      (when *object-reference-logging*
        (let [obj-data (PyObject. (Pointer. pyobj-value))]
          (log-info (format "tracking object  - 0x%x:%4d:%s"
                            pyobj-value
                            (.ob_refcnt obj-data)
                            py-type-name))))
      ;;We ask the garbage collector to track the python object and notify
      ;;us when it is released.  We then decref on that event.
      (resource/track pyobj
                      #(with-interpreter interpreter
                         (try
                           (when *object-reference-logging*
                             (let [obj-data (PyObject. (Pointer. pyobj-value))]
                               (log-info (format "releasing object - 0x%x:%4d:%s"
                                                 pyobj-value
                                                 (.ob_refcnt obj-data)
                                                 py-type-name))))
                           (libpy/Py_DecRef (Pointer. pyobj-value))
                           (catch Throwable e
                             (log-error "Exception while releasing object: %s" e))))
                      [:gc]))))


(defn incref-wrap-pyobject
  "Increment the object's refcount and then call wrap-pyobject.  Used for borrowed
  references that need to escape the current scope."
  [pyobj]
  (with-gil
    (let [pyobj (jna/as-ptr pyobj)]
      (libpy/Py_IncRef pyobj)
      (wrap-pyobject pyobj))))


(defn incref
  "Incref and return object"
  [pyobj]
  (let [pyobj (jna/as-ptr pyobj)]
    (libpy/Py_IncRef pyobj)
    pyobj))


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


;; Now we can completely implement ->python
(defn ->python
  "Completely convert a jvm object to a python copy."
  [item & [options]]
  (if item
    (py-proto/->python item options)
    (py-none)))


(defn py-raw-type
  ^Pointer [pyobj]
  (let [pyobj (PyObject. (jna/as-ptr pyobj))]
    (.ob_type pyobj)))


(extend-protocol py-proto/PPythonType
  Pointer
  (get-python-type [pyobj]
    (with-gil
    (-> pyobj
        py-raw-type
        pyinterp/py-type-keyword)))
  PyObject
  (get-python-type [item]
    (py-proto/get-python-type (.getPointer item))))


(defn py-string->string
  ^String [pyobj]
  (with-gil
    (when-not (= :str (python-type pyobj))
      (throw (ex-info (format "Object passed in is not a string: %s"
                              (python-type pyobj))
                      {})))
    (let [size-obj (jna/size-t-ref)
          ^Pointer str-ptr (libpy/PyUnicode_AsUTF8AndSize pyobj size-obj)
          n-elems (jna/size-t-ref-value size-obj)]
      (-> (.decode StandardCharsets/UTF_8 (.getByteBuffer str-ptr 0 n-elems))
          (.toString)))))


(defn py-str
  ^String [pyobj]
  (with-gil
    (let [py-str (if (= :str (python-type pyobj))
                   pyobj
                   (-> (libpy/PyObject_Str pyobj)
                       wrap-pyobject))]
      (py-string->string py-str))))


(defn py-dir
  [pyobj]
  (with-gil
    (-> (libpy/PyObject_Dir pyobj)
        wrap-pyobject
        (py-proto/->jvm {}))))


(defn ->py-long
  [item]
  (with-gil
    (wrap-pyobject
     (libpy/PyLong_FromLongLong (long item)))))


(defn ->py-float
  [item]
  (with-gil
    (wrap-pyobject
     (libpy/PyFloat_FromDouble (double item)))))


(defn ->py-string
  [item]
  (with-gil
    (let [byte-data (.getBytes ^String item StandardCharsets/UTF_16)]
      (wrap-pyobject
       (libpy/PyUnicode_Decode byte-data (dtype/ecount byte-data)
                               "UTF-16" "strict")))))


(defn ->py-dict
  [item]
  (with-gil
    (let [dict (libpy/PyDict_New)]
      (doseq [[k v] item]
        (libpy/PyDict_SetItem dict (->python k)
                              (->python v)))
      (wrap-pyobject
       dict))))


(defn ->py-list
  [item-seq]
  (with-gil
    (let [retval (libpy/PyList_New (count item-seq))]
      (->> item-seq
           (map-indexed (fn [idx item]
                          (libpy/PyList_SetItem
                           retval
                           idx
                           (let [new-val (->python item)]
                             (libpy/Py_IncRef new-val)
                             new-val))))
           dorun)
      (wrap-pyobject retval))))


(defn ->py-tuple
  [item-seq]
  (with-gil
    (let [n-items (count item-seq)
          new-tuple (libpy/PyTuple_New n-items)]
      (->> item-seq
           (map-indexed (fn [idx item]
                          (libpy/PyTuple_SetItem
                           new-tuple
                           idx
                           (let [new-val (->python item)]
                             (libpy/Py_IncRef new-val)
                             new-val))))
           dorun)
      (wrap-pyobject new-tuple))))


(defn ->py-set
  [item]
  (with-gil
    (-> (libpy/PySet_New (->py-list item))
        wrap-pyobject)))



(extend-protocol libpy-base/PToPyObjectPtr
  Number
  (convertible-to-pyobject-ptr? [item] true)
  (->py-object-ptr [item]
    (with-gil
      (->python item)))
  String
  (convertible-to-pyobject-ptr? [item] true)
  (->py-object-ptr [item]
    (with-gil
      (->python item)))
  ;;The container classes are mirrored into python, not copied.
  ;;so no entries here for map, list, etc.
  )


;; Chosen by fair dice roll, the item cutoff decides how many items
;; a persistent vector should have in it before it is considered a list
;; instead of a tuple.  If you know something should always be a tuple, then
;; call ->py-tuple explicitly.
(def ^:dynamic *item-tuple-cutoff* 8)


(defn wrap-clojure-fn
  [fn-obj]
  (when-not (fn? fn-obj)
    (throw (ex-info "This is not a function." {})))
  (reify CFunction$TupleFunction
    (pyinvoke [this self args]
      (try
        (let [retval
              (apply fn-obj (->jvm args))]
          (if (nil? retval)
            (libpy/Py_None)
            (->python retval)))
        (catch Throwable e
          (log-error (format "%s:%s" e (with-out-str
                                         (st/print-stack-trace e))))
          (libpy/PyErr_SetString (libpy/PyExc_Exception)
                                 (format "%s:%s" e (with-out-str
                                                     (st/print-stack-trace e))))
          nil)))))


(defn apply-method-def-data!
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
                           (throw (ex-info (format "Failed due to type: %s"
                                                   (type callback))))))
        name-ptr (jna/string->ptr name)
        doc-ptr (jna/string->ptr doc)]
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


(defn ->py-fn
  "Create a python callback from a clojure fn.
  If clojure fn, then tuple arguments are used.  If keyword arguments are desired,
  the pass in something derived from: libpython-clj.jna.CFunction$KeyWordFunction.
  If a pure fn is passed in, arguments are marshalled from python if possible and
  then to-python in the case of successful execution.  An exception will set the error
  indicator."
  [fn-obj {:keys [method-name documentation py-self]
           :or {method-name "unnamed_function"
                documentation "not documented"}}]
  (with-gil
    (let [py-self (or py-self (->python {}))]
      (wrap-pyobject (libpy/PyCFunction_New (method-def-data->method-def
                                             {:name method-name
                                              :doc documentation
                                              :function fn-obj})
                                            ;;This is a nice little tidbit, cfunction_new
                                            ;;steals the reference.
                                            (libpy/Py_IncRef py-self))))))



(extend-protocol py-proto/PCopyToPython
  Number
  (->python [item options]
    (if (integer? item)
      (->py-long item)
      (->py-float item)))
  String
  (->python [item options]
    (->py-string item))
  Symbol
  (->python [item options]
    (->py-string (name item)))
  Keyword
  (->python [item optins]
    (->py-string (name item)))
  Boolean
  (->python [item options]
    (if item
      (py-true)
      (py-false)))
  IPersistentMap
  (->python [item options]
    (->py-dict item))
  Map
  (->python [item options]
    (->py-dict item))
  Map$Entry
  (->python [item options]
    (->py-tuple [(.getKey item) (.getValue item)]))
  Set
  (->python [item options]
    (->py-set item))
  IPersistentSet
  (->python [item options]
    (->py-set item))
  RandomAccess
  (->python [item options]
    (if (and (instance? IPersistentVector item)
             (< (count item) (long *item-tuple-cutoff*)))
      (->py-tuple item)
      (->py-list item)))
  Iterable
  (->python [item options]
    (->py-list item))
  Pointer
  (->python [item options] item)
  PyObject
  (->python [item options] (.getPointer item))
  Tensor
  (->python [item options] (py-proto/as-numpy item options))
  TypedBuffer
  (->python [item options] (py-proto/as-numpy item options))
  Object
  (->python [item options]
    (cond
      (fn? item)
      (->py-fn item {})
      (casting/numeric-type? (dtype/get-datatype item))
      (py-proto/as-numpy item options)
      :else
      (if-let [item-reader (dtype/->reader item)]
        (->py-list item-reader)
        ;;Out of sane options at the moment.
        (throw (ex-info (format "Unable to convert java object to python: %s"
                                (str item))
                        {}))))))


(extend-protocol py-proto/PPythonType
  Number
  (get-python-type [item]
    (if (integer? item)
      :int
      :float))
  Boolean
  (get-python-type [item] :bool)
  String
  (get-python-type [item] :str)
  Symbol
  (get-python-type [item] :str)
  Keyword
  (get-python-type [item] :str)
  Map
  (get-python-type [item] :dict)
  IPersistentMap
  (get-python-type [item] :dict)
  Map$Entry
  (get-python-type [item] :tuple)
  Set
  (get-python-type [item] :set)
  IPersistentSet
  (get-python-type [item] :set)
  IPersistentVector
  (get-python-type [item]
    ;; fair dice roll
    (if (< (count item) (long *item-tuple-cutoff*))
      :tuple
      :list))
  RandomAccess
  (get-python-type [item] :list)
  Iterable
  (get-python-type [item] :list)
  Object
  (get-python-type [item]
    (if (casting/numeric-type? (dtype/get-datatype item))
      :nd-array
      (if (dtype-proto/convertible-to-reader? item)
        :list
        :unknown-type))))


(defn stringable?
  [item]
  (or (keyword? item)
      (string? item)
      (symbol? item)))


(defn stringable
  ^String [item]
  (when (stringable? item)
    (if (string? item)
      item
      (name item))))


(defn has-attr?
  [pyobj attr-name]
  (with-gil
    (= 1
       (if (stringable? attr-name)
         (libpy/PyObject_HasAttrString pyobj (stringable attr-name))
         (libpy/PyObject_HasAttr pyobj (->python attr-name))))))


(defn get-attr
  [pyobj attr-name]
  (with-gil
    (-> (if (stringable? attr-name)
          (libpy/PyObject_GetAttrString pyobj (stringable attr-name))
          (libpy/PyObject_GetAttr pyobj (->python attr-name)))
        wrap-pyobject)))


(defn set-attr!
  [pyobj attr-name attr-value]
  (with-gil
    (let [py-value (->python attr-value)]
      (if (stringable? attr-name)
        (libpy/PyObject_SetAttrString pyobj
                                      (stringable attr-name)
                                      py-value)
        (libpy/PyObject_SetAttr pyobj
                                (->python attr-name)
                                py-value)))
    pyobj))


(defn obj-has-item?
  [elem elem-name]
  (with-gil
    (= 1
       (if (stringable? elem-name)
         (libpy/PyMapping_HasKeyString elem (stringable elem-name))
         (libpy/PyMapping_HasKey elem (->python elem-name))))))


(defn obj-get-item
  [elem elem-name]
  (with-gil
    (-> (libpy/PyObject_GetItem elem (->python elem-name))
        wrap-pyobject)))


(defn obj-set-item!
  [elem elem-name elem-value]
  (with-gil
    (let [py-value (->python elem-value)]
      (libpy/PyObject_SetItem elem
                              (->python elem-name)
                              (->python elem-value)))
    elem))


(extend-protocol py-proto/PPyObject
  Pointer
  (dir [item]
    (py-dir item))
  (has-attr? [item name] (has-attr? item name))
  (attr [item name] (get-attr item name))
  (set-attr! [item item-name item-value]
    (set-attr! item item-name item-value))
  (callable? [item] (= 1 (libpy/PyCallable_Check item)))
  (has-item? [item item-name] (obj-has-item? item item-name))
  (item [item item-name] (obj-get-item item item-name))
  (set-item! [item item-name item-value]
    (obj-set-item! item item-name item-value))
  PyObject
  (dir [item] (py-proto/dir (.getPointer item)))
  (has-attr? [item item-name] (py-proto/has-attr? (.getPointer item) item-name))
  (attr [item item-name] (py-proto/attr (.getPointer item) item-name))
  (set-attr! [item item-name item-value]
    (py-proto/set-attr! (.getPointer item) item-name item-value))
  (callable? [item] (py-proto/callable? (.getPointer item)))
  (has-item? [item item-name] (py-proto/has-item? (.getPointer item) item-name))
  (item [item item-name] (py-proto/item (.getPointer item) item-name))
  (set-item! [item item-name item-value] (py-proto/set-item! (.getPointer item) item-name
                                                             item-value)))


(extend-protocol py-proto/PyCall
  Pointer
  (do-call-fn [callable arglist kw-arg-map]
    (with-gil nil
      (-> (cond
            (seq kw-arg-map)
            (libpy/PyObject_Call callable (->py-tuple arglist) (->py-dict kw-arg-map))
            (seq arglist)
            (libpy/PyObject_CallObject callable (->py-tuple arglist))
            :else
            (libpy/PyObject_CallObject callable nil))
          wrap-pyobject)))
  PyObject
  (do-call-fn [callable arglist kw-arg-map]
    (py-proto/do-call-fn (.getPointer callable) arglist kw-arg-map)))


(extend-protocol py-proto/PPyObjLength
  Pointer
  (len [item]
    (libpy/PyObject_Length item))
  PyObject
  (len [item]
    (py-proto/len (.getPointer item))))


(defn python->jvm-copy-hashmap
  [pyobj & [map-items]]
  (with-gil nil
    (when-not (= 1 (libpy/PyMapping_Check pyobj))
      (throw (ex-info (format "Object does not implement the mapping protocol: %s"
                              (python-type pyobj)))))
    (->> (or map-items
             (libpy/PyMapping_Items pyobj)
             wrap-pyobject)
         ->jvm
         (into {}))))


(defn python->jvm-copy-persistent-vector
  [pyobj]
  (with-gil nil
    (when-not (= 1 (libpy/PySequence_Check pyobj))
      (throw (ex-info (format "Object does not implement sequence protocol: %s"
                              (python-type pyobj)))))

    (->> (range (libpy/PySequence_Length pyobj))
         (mapv (fn [idx]
                 (-> (libpy/PySequence_GetItem pyobj idx)
                     wrap-pyobject
                     ->jvm))))))


(defn python->jvm-iterator
 [iter-fn item-conversion-fn]
 (with-gil nil
   (let [interpreter (ensure-bound-interpreter)]
     (let [py-iter (py-proto/call iter-fn)
           next-fn (fn [last-item]
                     (with-interpreter interpreter
                       (when-let [next-obj (libpy/PyIter_Next py-iter)]
                         (-> next-obj
                             (wrap-pyobject)
                             item-conversion-fn))))
           cur-item-store (atom (next-fn nil))]
       (reify ObjectIter
         jna/PToPtr
         (is-jna-ptr-convertible? [item] true)
         (->ptr-backing-store [item] py-iter)
         (hasNext [obj-iter] (boolean @cur-item-store))
         (next [obj-iter]
           (-> (swap-vals! cur-item-store next-fn)
               first))
         (current [obj-iter]
           @cur-item-store))))))


(defn python->jvm-iterable
  "Create an iterable that auto-copies what it iterates completely into the jvm.  It
  maintains a reference to the python object, however, so this method isn't necessarily
  safe."
  [pyobj & [item-conversion-fn]]
  (with-gil nil
    (when-not (has-attr? pyobj "__iter__")
      (throw (ex-info (format "object is not iterable: %s"
                              (python-type pyobj))
                      {})))
    (let [item-conversion-fn (or item-conversion-fn ->jvm)
          iter-callable (get-attr pyobj "__iter__")
          interpreter (ensure-bound-interpreter)]
      (reify
        jna/PToPtr
        (is-jna-ptr-convertible? [item] true)
        (->ptr-backing-store [item] pyobj)
        Iterable
        (iterator [item]
          (python->jvm-iterator iter-callable item-conversion-fn))))))


(defmethod pyobject->jvm :int
  [pyobj]
  (with-gil nil
    (libpy/PyLong_AsLongLong pyobj)))


(defmethod pyobject->jvm :float
  [pyobj]
  (with-gil nil
    (libpy/PyFloat_AsDouble pyobj)))


(defmethod pyobject->jvm :none-type
  [pyobj]
  nil)


(defmethod pyobject->jvm :str
  [pyobj]
  (with-gil nil
    (py-string->string pyobj)))


(defn pyobj-true?
  [pyobj]
  (with-gil nil
    (= 1 (libpy/PyObject_IsTrue pyobj))))


(defmethod pyobject->jvm :bool
  [pyobj]
  (pyobj-true? pyobj))


(defmethod pyobject->jvm :tuple
  [pyobj]
  (python->jvm-copy-persistent-vector pyobj))


(defmethod pyobject->jvm :list
  [pyobj]
  (python->jvm-copy-persistent-vector pyobj))


(defmethod pyobject->jvm :dict
  [pyobj]
  (python->jvm-copy-hashmap pyobj))


(defmethod pyobject->jvm :set
  [pyobj]
  (with-gil
    (->> (python->jvm-iterable pyobj)
         set)))


(defmethod pyobject->jvm :default
  [pyobj]
  (cond
    ;;Things could implement mapping and sequence logically so mapping
    ;;takes precedence
    (= 1 (libpy/PyMapping_Check pyobj))
    (if-let [map-items (try (-> (libpy/PyMapping_Items pyobj)
                                wrap-pyobject)
                            (catch Throwable e nil))]
      (python->jvm-copy-hashmap pyobj map-items)
      (do
        ;;Ignore error.  The mapping check isn't thorough enough to work.
        (libpy/PyErr_Clear)
        (python->jvm-copy-persistent-vector pyobj)))
    ;;Sequences become persistent vectors
    (= 1 (libpy/PySequence_Check pyobj))
    (python->jvm-copy-persistent-vector)
    :else
    {:type (python-type pyobj)
     :value (Pointer/nativeValue (jna/as-ptr pyobj))}))
