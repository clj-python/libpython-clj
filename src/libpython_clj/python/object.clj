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
                     ensure-interpreter
                     ensure-bound-interpreter
                     check-error-throw]
             :as pyinterp]
            [libpython-clj.python.protocols
             :refer [pyobject->jvm
                     python-type]
             :as py-proto]
            [libpython-clj.jna.base :as libpy-base]
            [libpython-clj.jna :as libpy]
            [libpython-clj.python.gc :as pygc]
            [clojure.stacktrace :as st]
            [tech.jna :as jna]
            [tech.v2.datatype :as dtype]
            [tech.v2.datatype.protocols :as dtype-proto]
            [tech.v2.datatype.casting :as casting]
            [tech.resource :as resource]
            [tech.v2.tensor]
            [clojure.tools.logging :as log])
  (:import [com.sun.jna Pointer CallbackReference]
           [com.sun.jna.ptr PointerByReference]
           [java.lang.reflect Field]
           [libpython_clj.jna
            PyObject
            CFunction$KeyWordFunction
            CFunction$TupleFunction
            CFunction$NoArgFunction
            PyMethodDef]
           [java.nio.charset StandardCharsets]
           [tech.v2.datatype ObjectIter ObjectReader]
           [tech.v2.datatype.typed_buffer TypedBuffer]
           [tech.v2.tensor.protocols PTensor]
           [java.util RandomAccess Map Set Map$Entry]
           [clojure.lang Symbol Keyword
            IPersistentMap
            IPersistentVector
            IPersistentSet
            Range LongRange]))


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
  (when-not (nil? item)
    (py-proto/->jvm item options)))


(def object-reference-logging (atom false))


(defn incref
  "Incref and return object"
  [pyobj]
  (let [pyobj (libpy/as-pyobj pyobj)]
    (libpy/Py_IncRef pyobj)
    pyobj))


(defn refcount
  ^long [pyobj]
  (long (.ob_refcnt (PyObject. (libpy/as-pyobj pyobj)))))


(def ^:private non-native-val
  (delay (Pointer/nativeValue (libpy/as-pyobj (libpy/Py_None)))))


(defn wrap-pyobject
  "Wrap object such that when it is no longer accessible via the program decref is
  called. Used for new references.  This is some of the meat of the issue, however,
  in that getting the two system's garbage collectors to play nice is kind
  of tough.
  This is a hot path; it is called quite a lot from a lot of places."
  ([pyobj skip-check-error?]
   ;;We don't wrap pynone
   (if pyobj
     (let [pyobj-value (Pointer/nativeValue (libpy/as-pyobj pyobj))
           ^PyObject obj-data (when @object-reference-logging
                                (PyObject. (Pointer. pyobj-value)))]
       (if (not= pyobj-value
                 (long @non-native-val))
         (do
           (ensure-bound-interpreter)
           (when @object-reference-logging
             (println (format "tracking object  - 0x%x:%4d:%s"
                              pyobj-value
                              (.ob_refcnt obj-data)
                              (name (python-type pyobj)))))
           ;;We ask the garbage collector to track the python object and notify
           ;;us when it is released.  We then decref on that event.
           (pygc/track
            pyobj
            ;;No longer with-gil.  Because cleanup is cooperative, the gil is
            ;;guaranteed to be captured here already.
            #(try
               ;;Intentionally overshadow pyobj.  We cannot access it here.
               (let [pyobj (Pointer. pyobj-value)]
                 (when @object-reference-logging
                   (let [_ (.read obj-data)
                         refcount (.ob_refcnt obj-data)]
                     (if (< refcount 1)
                       (log/errorf "Fatal error -- releasing object - 0x%x:%4d:%s
Object's refcount is bad.  Crash is imminent"
                                   pyobj-value
                                   refcount
                                   (name (python-type pyobj)))
                       (println (format "releasing object - 0x%x:%4d:%s"
                                        pyobj-value
                                        refcount
                                        (name (python-type pyobj))))))))
               (libpy/Py_DecRef pyobj)
               (catch Throwable e
                 (log/error e "Exception while releasing object"))))
           (when-not skip-check-error? (check-error-throw))
           pyobj)
         (do
           ;;Special handling for PyNone types
           (libpy/Py_DecRef pyobj)
           (when-not skip-check-error? (check-error-throw))
           nil)))
     (when-not skip-check-error? (check-error-throw))))
  ([pyobj]
   (wrap-pyobject pyobj false)))


(defmacro stack-resource-context
  [& body]
  `(pygc/with-stack-context
     ~@body))


(defn incref-wrap-pyobject
  "Increment the object's refcount and then call wrap-pyobject.  Used for borrowed
  references that need to escape the current scope."
  [pyobj]
  (with-gil
    (let [pyobj (libpy/as-pyobj pyobj)]
      (libpy/Py_IncRef pyobj)
      (wrap-pyobject pyobj))))


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
  (if (nil? item)
    (py-none)
    (py-proto/->python item options)))


(defn py-raw-type
  ^Pointer [pyobj]
  (let [pyobj (PyObject. (libpy/as-pyobj pyobj))]
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
  "Given a python string return a string"
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
  "Call the __str__ attribute on an object return a new string pyobject"
  ^String [pyobj]
  (with-gil
    (let [py-str (if (= :str (python-type pyobj))
                   pyobj
                   (-> (libpy/PyObject_Str pyobj)
                       wrap-pyobject))]
      (py-string->string py-str))))


(defn py-dir
  "List the attribute names of an object"
  [pyobj]
  (with-gil
    (-> (libpy/PyObject_Dir pyobj)
        wrap-pyobject
        (py-proto/->jvm {}))))


(defn ->py-long
  "Convert an object into a python long"
  [item]
  (with-gil
    (wrap-pyobject
     (libpy/PyLong_FromLongLong (long item)))))


(defn ->py-float
  "Convert an object into a python float"
  [item]
  (with-gil
    (wrap-pyobject
     (libpy/PyFloat_FromDouble (double item)))))


(defn ->py-string
  "Copy an object into a python string"
  [item]
  (with-gil
    (let [byte-data (.getBytes ^String item StandardCharsets/UTF_16)]
      (wrap-pyobject
       (libpy/PyUnicode_Decode byte-data (dtype/ecount byte-data)
                               "UTF-16" "strict")))))


(defn ->py-dict
  "Copy an object into a new python dictionary."
  [item]
  (with-gil
    (let [dict (libpy/PyDict_New)]
      (doseq [[k v] item]
        (libpy/PyDict_SetItem dict (->python k)
                              (->python v)))
      (wrap-pyobject
       dict))))


(defn ->py-list
  "Copy an object into a new python list."
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
  "Copy an object into a new python tuple"
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
  Character
  (convertible-to-pyobject-ptr? [item] true)
  (->py-object-ptr [item]
    (with-gil
      (->python (str item))))
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


(defn- cfunc-instance?
  [function]
  (or (instance? CFunction$KeyWordFunction function)
      (instance? CFunction$TupleFunction function)
      (instance? CFunction$NoArgFunction function)))


(defn apply-method-def-data!
  [^PyMethodDef method-def {:keys [name
                                   doc
                                   function]
                            :as method-data}]
  ;;Here we really do need a resource stack context
  (when-not (cfunc-instance? function)
    (throw (Exception.
            (format "Callbacks must implement one of the CFunction interfaces:
%s" (type function)))))
  (let [meth-flags (long (cond
                           (instance? CFunction$NoArgFunction function)
                           @libpy/METH_NOARGS

                           (instance? CFunction$TupleFunction function)
                           @libpy/METH_VARARGS

                           (instance? CFunction$KeyWordFunction function)
                           (bit-or @libpy/METH_KEYWORDS @libpy/METH_VARARGS)
                           :else
                           (throw (ex-info (format "Failed due to type: %s"
                                                   (type function))
                                           {}))))
        name-ptr (jna/string->ptr-untracked name)
        doc-ptr (jna/string->ptr-untracked doc)]
    (set! (.ml_name method-def) name-ptr)
    (set! (.ml_meth method-def) (CallbackReference/getFunctionPointer function))
    (set! (.ml_flags method-def) (int meth-flags))
    (set! (.ml_doc method-def) doc-ptr)
    (.write method-def)
    (pyinterp/conj-forever! (assoc method-data
                                   :name-ptr name-ptr
                                   :doc-ptr doc-ptr
                                   :callback-object function
                                   :method-definition method-def))
    method-def))


(defn method-def-data->method-def
  [method-data]
  (apply-method-def-data! (PyMethodDef.) method-data))



(defn- cfunc-impl->pyobject
  [cfunc {:keys [method-name
                 documentation
                 py-self]
          :or {method-name "unnamed_function"
               documentation "not documented"}}]
  (with-gil
    ;;This is a nice little tidbit, cfunction_new
    ;;steals the reference.
    (let [py-self (when py-self (incref (->python py-self)))]
      (-> (libpy/PyCFunction_New (method-def-data->method-def
                                  {:name method-name
                                   :doc documentation
                                   :function cfunc})
                                 py-self)
          (wrap-pyobject)))))


(defn py-tuple->borrowed-reference-reader
  "Given a python tuple, return an object reader that iterates
  through the items.  If you hold onto one of these items you need
  to add to it's reference count.  If unsure of what you are doing
  don't use this method, use ->jvm."
  [tuple]
  (with-gil
    (let [interpreter (ensure-bound-interpreter)
          n-items (long (libpy/PyObject_Length tuple))]
      (reify ObjectReader
        (lsize [_] n-items)
        (read [_ idx]
          (with-gil
            (libpy/PyTuple_GetItem tuple idx)))))))


(defn ->python-incref
  "Convert to python and add a reference.  This is necessary for return values from
  functions as the ->python pathway adds a reference but it also tracks it and
  releases it when it is not in use any more.  Thus python ends up holding onto
  something with fewer refcounts than it should have.  If you are just messing
  around in the repl you only need ->python.  There is an expectation that the
  return value of a function call is a new reference and not a borrowed reference
  hence this pathway."
  [item]
  (-> (->python item)
      (incref)))



(defn make-tuple-fn
  "Given a clojure function, create a python tuple function.
  arg-convert is applied to arguments before the clojure function
  gets them and result-converter is applied to the outbound result.
  Exceptions are caught, logged, and propagated to python.

  arg-converter: A function to be called on arguments before they get to
    clojure.  Defaults to ->jvm.
  result-converter: A function to be called on the return value before it
    makes it back to python.  Defaults to ->python-incref.
  method-name: Name of function exposed to python.
  documentation: Documentation of function exposed to python."
  [fn-obj & {:keys [arg-converter
                    result-converter]
             :or {arg-converter ->jvm
                  result-converter ->python-incref}
             :as options}]
  (with-gil
    (-> (reify CFunction$TupleFunction
          (pyinvoke [this self args]
            (try
              (let [argseq (cond->> (py-tuple->borrowed-reference-reader args)
                             arg-converter
                             (map arg-converter))]
                (cond-> (apply fn-obj argseq)
                  result-converter
                  (result-converter)))
              (catch Throwable e
                (log/error e "Error executing clojure function.")
                (libpy/PyErr_SetString (libpy/PyExc_Exception)
                                       (format "%s:%s" e (with-out-str
                                                           (st/print-stack-trace e))))
                nil))))
        (cfunc-impl->pyobject options))))


(defn ->py-fn
  "Create a python callback from a clojure fn.
  If clojure fn, then tuple arguments are used.  If keyword arguments are desired,
  the pass in something derived from: libpython-clj.jna.CFunction$KeyWordFunction.
  If a pure fn is passed in, arguments are marshalled from python if possible and
  then to-python in the case of successful execution.  An exception will set the error
  indicator.
  Options are
  method-name: Name of function exposed to python.
  documentation: Documentation of function exposed to python.
  py-self: The 'self' object to be used for the function."
  ([fn-obj {:keys []
            :as options}]
   (cond
     (instance? clojure.lang.IFn fn-obj)
     (apply make-tuple-fn fn-obj (apply concat options))
     (cfunc-instance? fn-obj)
     (cfunc-impl->pyobject fn-obj options)
     :else
     (throw (Exception. "fn-obj is neither a CFunction nor clojure callable."))))
  ([fn-obj]
   (->py-fn fn-obj {})))


(defn py-fn->instance-fn
  "Given a python callable, return an instance function meant to be used
  in class definitions."
  [py-fn]
  (with-gil
    (-> (libpy/PyInstanceMethod_New py-fn)
        (wrap-pyobject))))


(defn make-tuple-instance-fn
  "Make an instance function.  In this case the default behavior is to
  pass raw python object ptr args  to the clojure function without marshalling
  as that can add confusion and unnecessary overhead.  Self will be the first argument.
  Callers can change this behavior by setting the 'arg-converter' option as in
  'make-tuple-fn'.
  Options are the same as make-tuple-fn."
  [clj-fn & {:keys [arg-converter]
             :as options}]
  (with-gil
    (-> (apply make-tuple-fn
               clj-fn
               ;;Explicity set arg-convert to override make-tuple-fn's default
               ;;->jvm arg-converter.
               (->> (assoc options :arg-converter arg-converter)
                    (apply concat)))
        ;;Mark this as an instance function.
        (py-fn->instance-fn))))


(defn create-class
  "Create a new class object.  Any callable values in the cls-hashmap
  will be presented as instance methods.
  Things in the cls hashmap had better be either atoms or already converted
  python objects.  You may get surprised otherwise; you have been warned.
  See the classes-test file in test/libpython-clj"
  [name bases cls-hashmap]
  (with-gil
    (let [cls-dict (reduce (fn [cls-dict [k v]]
                             (py-proto/set-item! cls-dict k (->python v))
                             cls-dict)
                           (->py-dict {})
                           cls-hashmap)
          bases (->py-tuple bases)
          new-cls (py-proto/call (libpy/PyType_Type) name bases cls-dict)]
      (py-proto/as-jvm new-cls nil))))

(def ^:private lr-step-field (doto (.getDeclaredField ^Class LongRange "step")
                               (.setAccessible true)))


(def ^:private r-step-field (doto (.getDeclaredField ^Class Range "step")
                              (.setAccessible true)))


(extend-protocol py-proto/PCopyToPython
  Number
  (->python [item options]
    (if (integer? item)
      (->py-long item)
      (->py-float item)))
  String
  (->python [item options]
    (->py-string item))
  Character
  (->python [item options]
    (->py-string (str item)))
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
  Range
  (->python [item options]
    (if (casting/integer-type? (dtype/get-datatype item))
      (let [start (first item)
            step (.get ^Field r-step-field item)
            stop (+ start (* step (count item)))]
        (py-proto/call (libpy/PyRange_Type) start stop step))
      (->py-list item)))
  LongRange
  (->python [item options]
    (let [start (first item)
          step (.get ^Field lr-step-field item)
          stop (+ start (* step (count item)))]
      (py-proto/call (libpy/PyRange_Type) start stop step)))
  Iterable
  (->python [item options]
    (cond
      (instance? RandomAccess item)
      (if (and (instance? IPersistentVector item)
               (< (count item) (long *item-tuple-cutoff*)))
        (->py-tuple item)
        (->py-list item))
      (instance? Map$Entry item)
      (->py-tuple [(.getKey ^Map$Entry item)
                   (.getValue ^Map$Entry item)])
      (or (set? item)
          (instance? Set item))
      (->py-set item)
      ;;Careful here!
      (fn? item)
      (->py-fn item)
      (or (map? item)
          (instance? Map item))
      (->py-dict item)
      :else
      (->py-list item)))
  Pointer
  (->python [item options] item)
  PyObject
  (->python [item options] (.getPointer item))
  PTensor
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
        (throw (Exception. (format "Unable to convert java object to python: %s"
                                   (type item))))))))


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
  Map$Entry
  (get-python-type [item] :tuple)
  Iterable
  (get-python-type [item]
    (cond
      (instance? RandomAccess item)
      (if (and (instance? IPersistentVector item)
               (< (count item) (long *item-tuple-cutoff*)))
        :tuple
        :list)
      (or (instance? Set item)
          (instance? IPersistentSet item))
      :set
      (or (instance? Map item)
          (instance? IPersistentMap item))
      :dict
      :else
      :list))
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
  (get-attr [item name] (get-attr item name))
  (set-attr! [item item-name item-value] (set-attr! item item-name item-value))
  (callable? [item] (= 1 (libpy/PyCallable_Check item)))
  (has-item? [item item-name] (obj-has-item? item item-name))
  (get-item [item item-name] (obj-get-item item item-name))
  (set-item! [item item-name item-value] (obj-set-item! item item-name item-value))
  PyObject
  (dir [item] (py-proto/dir (.getPointer item)))
  (has-attr? [item item-name] (py-proto/has-attr? (.getPointer item) item-name))
  (get-attr [item item-name] (py-proto/get-attr (.getPointer item) item-name))
  (set-attr! [item item-name item-value]
    (py-proto/set-attr! (.getPointer item) item-name item-value))
  (callable? [item] (py-proto/callable? (.getPointer item)))
  (has-item? [item item-name] (py-proto/has-item? (.getPointer item) item-name))
  (item [item item-name] (py-proto/get-item (.getPointer item) item-name))
  (set-item! [item item-name item-value]
    (py-proto/set-item! (.getPointer item) item-name item-value)))


;;This one is dangerous.  But there are times (like in the actual type object)
;;that we don't want to be wrapping the results in any way; they are getting passed
;;directly to python and not to java.
(def ^:dynamic *passthrough-exceptions* false)


(extend-protocol py-proto/PyCall
  Pointer
  (do-call-fn [callable arglist kw-arg-map]
    (with-gil
      (-> (cond
            (seq kw-arg-map)
            (libpy/PyObject_Call callable
                                 (->py-tuple arglist)
                                 (->py-dict kw-arg-map))
            (seq arglist)
            (libpy/PyObject_CallObject callable (->py-tuple arglist))
            :else
            (libpy/PyObject_CallObject callable nil))
          (wrap-pyobject *passthrough-exceptions*))))
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
  (with-gil
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
  (with-gil
    (when-not (= 1 (libpy/PySequence_Check pyobj))
      (throw (ex-info (format "Object does not implement sequence protocol: %s"
                              (python-type pyobj)))))

    (->> (range (libpy/PySequence_Length pyobj))
         (mapv (fn [idx]
                 (-> (libpy/PySequence_GetItem pyobj idx)
                     wrap-pyobject
                     ->jvm))))))


(defn python->jvm-iterator
  "This is a tough function to get right.  The iterator could return nil as in
  you could have a list of python none types or something so you have to iterate
  till you get a StopIteration error."
  [iter-fn & [item-conversion-fn]]
  (with-gil
    (let [interpreter (ensure-bound-interpreter)]
      (let [py-iter (py-proto/call iter-fn)
            py-next-fn (when py-iter (py-proto/get-attr py-iter "__next__"))
            next-fn (fn [last-item]
                      (with-gil
                        (let [retval (libpy/PyObject_CallObject py-next-fn nil)]
                          (if (libpy/PyErr_Occurred)
                            (let [ptype (PointerByReference.)
                                  pvalue (PointerByReference.)
                                  ptraceback (PointerByReference.)
                                  _ (libpy/PyErr_Fetch ptype pvalue ptraceback)
                                  ptype (jna/->ptr-backing-store ptype)
                                  pvalue (jna/->ptr-backing-store pvalue)
                                  ptraceback (jna/->ptr-backing-store ptraceback)]
                              (if (= ptype
                                     (libpy/PyExc_StopIteration))
                                (do
                                  (libpy/Py_DecRef ptype)
                                  (when pvalue (libpy/Py_DecRef pvalue))
                                  (when ptraceback (libpy/Py_DecRef ptraceback))
                                  nil)
                                (do (libpy/PyErr_Restore ptype pvalue ptraceback)
                                    (check-error-throw))))
                            [(cond-> (wrap-pyobject retval)
                               item-conversion-fn
                               item-conversion-fn)]))))
            cur-item-store (atom (next-fn nil))]
        (reify ObjectIter
          jna/PToPtr
          (is-jna-ptr-convertible? [item] true)
          (->ptr-backing-store [item] py-iter)
          (hasNext [obj-iter]
            (not (nil? @cur-item-store)))
          (next [obj-iter]
            (-> (swap-vals! cur-item-store next-fn)
                ffirst))
          (current [obj-iter]
            (first @cur-item-store)))))))


(defn python->jvm-iterable
  "Create an iterable that auto-copies what it iterates completely into the jvm.  It
  maintains a reference to the python object, however, so this method isn't necessarily
  safe."
  [pyobj & [item-conversion-fn]]
  (with-gil
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
  (with-gil
    (libpy/PyLong_AsLongLong pyobj)))


(defmethod pyobject->jvm :float
  [pyobj]
  (with-gil
    (libpy/PyFloat_AsDouble pyobj)))


(defmethod pyobject->jvm :none-type
  [pyobj]
  nil)


(defmethod pyobject->jvm :str
  [pyobj]
  (with-gil
    (py-string->string pyobj)))


(defn pyobj-true?
  [pyobj]
  (with-gil
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
  (with-gil
    (let [ppos (jna/size-t-ref 0)
          pkey (PointerByReference.)
          pvalue (PointerByReference.)
          retval (java.util.ArrayList.)]
      ;;Dictionary iteration doesn't appear to be reentrant so we have
      ;;to do 2 passes.
      (loop [next-retval (libpy/PyDict_Next pyobj ppos pkey pvalue)]
        (if (not= 0 next-retval)
          (do
            (.add retval [(libpy/as-pyobj pkey)
                          (libpy/as-pyobj pvalue)])
            (recur (libpy/PyDict_Next pyobj ppos pkey pvalue)))
          (->> retval
               (map (fn [[k v]]
                      [(->jvm k) (->jvm v)]))
               (into {})))))))


(defmethod pyobject->jvm :set
  [pyobj]
  (with-gil
    (->> (python->jvm-iterable pyobj)
         set)))


;;numpy types
(defn numpy-scalar->jvm
  [pyobj]
  (with-gil
    (-> (py-proto/get-attr pyobj "data")
        (py-proto/get-item (->py-tuple []))
        ->jvm)))

(defmethod pyobject->jvm :uint-8
  [pyobj]
  (numpy-scalar->jvm pyobj))


(defmethod pyobject->jvm :int-8
  [pyobj]
  (numpy-scalar->jvm pyobj))


(defmethod pyobject->jvm :uint-16
  [pyobj]
  (numpy-scalar->jvm pyobj))


(defmethod pyobject->jvm :int-16
  [pyobj]
  (numpy-scalar->jvm pyobj))


(defmethod pyobject->jvm :uint-32
  [pyobj]
  (numpy-scalar->jvm pyobj))


(defmethod pyobject->jvm :int-32
  [pyobj]
  (numpy-scalar->jvm pyobj))


(defmethod pyobject->jvm :uint-64
  [pyobj]
  (numpy-scalar->jvm pyobj))


(defmethod pyobject->jvm :int-64
  [pyobj]
  (numpy-scalar->jvm pyobj))


(defmethod pyobject->jvm :float-64
  [pyobj]
  (numpy-scalar->jvm pyobj))


(defmethod pyobject->jvm :float-32
  [pyobj]
  (numpy-scalar->jvm pyobj))


(defmethod pyobject->jvm :range
  [pyobj]
  (with-gil
    (let [start (->jvm (py-proto/get-attr pyobj "start"))
          step (->jvm (py-proto/get-attr pyobj "step"))
          stop (->jvm (py-proto/get-attr pyobj "stop"))]
      (range start stop step))))


(defmethod pyobject->jvm :default
  [pyobj]
  (with-gil
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
      (python->jvm-copy-persistent-vector pyobj)
      :else
      {:type (python-type pyobj)
       :value (Pointer/nativeValue (libpy/as-pyobj pyobj))})))


(defn is-instance?
  "Returns true if inst is an instance of type.
  False otherwise."
  [py-inst py-type]
  (with-gil
    (= 1 (libpy/PyObject_IsInstance (->python py-inst)
                                    ;;The type has to be a python type already.
                                    py-type))))


(defn hash-code
  ^long [py-inst]
  (with-gil
    (long (libpy/PyObject_Hash (->python py-inst)))))


(defn equals?
  "Returns true of the python equals operator returns 1."
  [lhs rhs]
  (with-gil
    (= 1 (libpy/PyObject_RichCompareBool (->python lhs)
                                         (->python rhs)
                                         :py-eq))))
