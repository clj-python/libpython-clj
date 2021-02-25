(ns libpython-clj2.python.bridge-as-python
  (:require [libpython-clj2.python.ffi :refer [with-gil] :as py-ffi]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.class :as py-class]
            [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.fn :as py-fn]
            [libpython-clj.python.gc :as pygc]
            [tech.v3.datatype.errors :as errors]
            [tech.v3.datatype :as dtype]
            [clojure.tools.logging :as log])
  (:import [java.util Map RandomAccess UUID List Iterator]
           [java.util.concurrent ConcurrentHashMap]
           [tech.v3.datatype.ffi Pointer]
           [tech.v3.datatype ObjectBuffer]
           [clojure.lang Keyword Symbol IFn]))


(defonce ^{:private true
           :tag ConcurrentHashMap}
  jvm-handle-map (ConcurrentHashMap.))


(defn identity-hash-code
  ^long [obj]
  (long (System/identityHashCode obj)))


(defn make-jvm-object-handle
  ^long [item]
  (let [^ConcurrentHashMap hash-map jvm-handle-map]
    (loop [handle (identity-hash-code item)]
      (if (not (.containsKey hash-map handle))
        (do
          (.put hash-map handle item)
          handle)
        (recur (.hashCode (UUID/randomUUID)))))))


(defn get-jvm-object
  [handle]
  (.get ^ConcurrentHashMap jvm-handle-map (long handle)))

(defn remove-jvm-object
  [handle]
  (.remove ^ConcurrentHashMap jvm-handle-map (long handle))
  nil)

(defn py-self->jvm-handle
  ^long [self]
  (long (py-base/->jvm (py-proto/get-attr self "jvm_handle"))))


(defn py-self->jvm-obj
  ^Object [self]
  (-> (py-self->jvm-handle self)
      get-jvm-object))


(defmacro pydelay
  "Create a delay object that uses only gc reference semantics.  If stack reference
  semantics happen to be in effect when this delay executes the object may still be
  reachable by your program when it's reference counts are released leading to
  bad/crashy behavior.  This ensures that can't happen at the cost of possibly an object
  sticking around."
  [& body]
  `(delay
     (py-ffi/with-gil
       (with-bindings {#'pygc/*stack-gc-context* nil}
         ~@body))))


(defn as-python-incref
  "Convert to python and add a reference.  Necessary for return values from
  functions as python expects a new reference and the as-python pathway
  ensures the jvm garbage collector also sees the reference."
  [item]
  (when-let [retval (py-base/as-python item)]
    (do
      (py-ffi/Py_IncRef retval)
      retval)))


(defn- as-tuple-instance-fn
  [fn-obj & [options]]
  (py-class/make-tuple-instance-fn fn-obj
                                   (merge {:result-converter as-python-incref}
                                          options)))


(defn self->list
  ^List [self]
  (py-self->jvm-obj self))


(defonce sequence-type*
  (pydelay
   (py-ffi/with-gil
     (py-ffi/with-decref
       [mod (py-ffi/PyImport_ImportModule "collections.abc")
        seq-type (py-ffi/PyObject_GetAttrString mod "MutableSequence")]
       (py-class/create-class
        "jvm-list-as-python"
        [seq-type]
        {"__init__" (as-tuple-instance-fn
                     (fn [self jvm-handle]
                       (py-proto/set-attr! self "jvm_handle" jvm-handle)
                       nil))
         "__del__" (as-tuple-instance-fn
                    #(try
                       (remove-jvm-object (py-self->jvm-handle %))
                       (catch Throwable e
                         (log/warnf e "Error removing object"))))
         "__contains__" (as-tuple-instance-fn #(.contains (self->list %1) %2))
         "__eq__" (as-tuple-instance-fn #(.equals (self->list %1)
                                                  (py-base/->jvm %2)))
         "__getitem__" (as-tuple-instance-fn #(.get (self->list %1)
                                                    (int (py-base/->jvm %2))))
         "__setitem__" (as-tuple-instance-fn #(.set (self->list %1)
                                                    (int (py-base/->jvm %2))
                                                    (py-base/as-jvm %3)))
         "__delitem__" (as-tuple-instance-fn #(.remove (self->list %1)
                                                       (int (py-base/->jvm %2))))
         "__hash__" (as-tuple-instance-fn #(.hashCode (self->list %1)))
         "__iter__" (as-tuple-instance-fn #(.iterator (self->list %1)))
         "__len__" (as-tuple-instance-fn #(.size (self->list %1)))
         "__str__" (as-tuple-instance-fn #(.toString (self->list %1)))
         "clear" (as-tuple-instance-fn #(.clear (self->list %1)))
         "sort" (as-tuple-instance-fn #(.sort (self->list %1) nil))
         "append" (as-tuple-instance-fn #(.add (self->list %1) %2))
         "insert" (as-tuple-instance-fn #(.add (self->list %1)
                                               (int (py-base/->jvm %2)) %3))
         "pop" (as-tuple-instance-fn
                (fn [self & args]
                  (let [jvm-data (self->list self)
                        args (map py-base/->jvm args)
                        index (int (if (first args)
                                     (first args)
                                     -1))
                        index (if (< index 0)
                                (- (.size jvm-data) index)
                                index)]
                    #(.remove jvm-data index))))})))))


(defn list-as-python
  [item]
  (let [list-data (if (instance? List item)
                    item
                    (dtype/->buffer item))
        hdl (make-jvm-object-handle list-data)]
    (@sequence-type* hdl)))


(defmethod py-proto/pyobject->jvm :jvm-list-as-python
  [pyobj opt]
  (py-self->jvm-obj pyobj))



(defn self->map
  ^Map [self]
  (py-self->jvm-obj self))


(defonce mapping-type*
  (pydelay
    (with-gil
      (py-ffi/with-decref
        [mod (py-ffi/PyImport_ImportModule "collections.abc")
         map-type (py-ffi/PyObject_GetAttrString mod "MutableMapping")]
        ;;In order to make things work ingeneral
        (py-class/create-class
         "jvm-map-as-python"
         [map-type]
         {"__init__" (as-tuple-instance-fn
                      (fn [self jvm-handle]
                        (py-proto/set-attr! self "jvm_handle" jvm-handle)
                        nil))
          "__del__" (as-tuple-instance-fn
                     #(try
                        (remove-jvm-object (py-self->jvm-handle %))
                        (catch Throwable e
                          (log/warnf e "Error removing object"))))
          "__contains__" (as-tuple-instance-fn #(.containsKey (self->map %1)
                                                              (py-base/as-jvm %2)))
          "__eq__" (as-tuple-instance-fn #(.equals (self->map %1) (py-base/as-jvm %2)))
          "__getitem__" (as-tuple-instance-fn
                         #(.get (self->map %1) (py-base/as-jvm %2)))
          "__setitem__" (as-tuple-instance-fn #(.put (self->map %1) (py-base/as-jvm %2) %3))
          "__delitem__" (as-tuple-instance-fn #(.remove (self->map %1) (py-base/as-jvm %2)))
          "__hash__" (as-tuple-instance-fn #(.hashCode (self->map %1)))
          "__iter__" (as-tuple-instance-fn #(.iterator ^Iterable (keys (self->map %1))))
          "__len__" (as-tuple-instance-fn #(.size (self->map %1)))
          "__str__" (as-tuple-instance-fn #(.toString (self->map %1)))
          "clear" (as-tuple-instance-fn #(.clear (self->map %1)))
          "keys" (as-tuple-instance-fn #(seq (.keySet (self->map %1))))
          "values" (as-tuple-instance-fn #(seq (.values (self->map %1))))
          "pop" (as-tuple-instance-fn #(.remove (self->map %1) (py-base/as-jvm %2)))})))))


(defn map-as-python
  [^Map jvm-data]
  (errors/when-not-errorf
   (instance? Map jvm-data)
   "arg (%s) is not an instance of Map" (type jvm-data))
  (@mapping-type* (make-jvm-object-handle jvm-data)))


(defmethod py-proto/pyobject->jvm :jvm-map-as-python
  [pyobj opt]
  (py-self->jvm-obj pyobj))


(def iterable-type*
  (pydelay
    (py-ffi/with-gil
      (py-ffi/with-decref
        [mod (py-ffi/PyImport_ImportModule "collections.abc")
         iter-base-cls (py-ffi/PyObject_GetAttrString mod  "Iterable")]
        (py-class/create-class
         "jvm-iterable-as-python"
         [iter-base-cls]
         {"__init__" (as-tuple-instance-fn
                      (fn [self jvm-handle]
                        (py-proto/set-attr! self "jvm_handle" jvm-handle)
                        nil))
          "__del__" (as-tuple-instance-fn
                     #(try
                        (remove-jvm-object (py-self->jvm-handle %))
                        (catch Throwable e
                          (log/warnf e "Error removing object"))))
          "__iter__" (as-tuple-instance-fn
                      #(.iterator ^Iterable (py-self->jvm-obj %))
                      {:name "__iter__"})
          "__eq__" (as-tuple-instance-fn #(.equals (py-self->jvm-obj %1)
                                                   (py-base/as-jvm %2))
                                         {:name "__eq__"})
          "__hash__" (as-tuple-instance-fn
                      #(.hashCode (py-self->jvm-obj %))
                      {:name "__hash__"})
          "__str__" (as-tuple-instance-fn
                     #(.toString (py-self->jvm-obj %))
                     {:name "__str__"})})))))


(defn iterable-as-python
  [^Iterable jvm-data]
  (errors/when-not-errorf
   (instance? Iterable jvm-data)
   "Argument (%s) is not an instance of Iterable" (type jvm-data))
  (@iterable-type* (make-jvm-object-handle jvm-data)))


(defmethod py-proto/pyobject->jvm :jvm-iterable-as-python
  [pyobj opt]
  (py-self->jvm-obj pyobj))


(def iterator-type*
  (pydelay
    (py-ffi/with-gil
      (let [mod (py-ffi/PyImport_ImportModule "collections.abc")
            iter-base-cls (py-ffi/PyObject_GetAttrString mod  "Iterator")
            next-fn (fn [self]
                      (let [^Iterator item (py-self->jvm-obj self)]
                        (if (.hasNext item)
                          (pygc/with-stack-context
                            (let [retval
                                  (-> (.next item)
                                      ;;As python tracks the object in a jvm context
                                      (py-base/as-python))]
                              ;;But we are handing the object back to python
                              ;;which is expecting a new reference.
                              (py-ffi/Py_IncRef retval)
                              retval))
                          (do
                            (py-ffi/PyErr_SetNone (py-ffi/py-exc-stopiter-type))
                            nil))))]
        (py-class/create-class
         "jvm-iterator-as-python"
         [iter-base-cls]
         {"__init__" (as-tuple-instance-fn
                      (fn [self jvm-handle]
                        (py-proto/set-attr! self "jvm_handle" jvm-handle)
                        nil))
          "__del__" (as-tuple-instance-fn
                     #(try
                        (remove-jvm-object (py-self->jvm-handle %))
                        (catch Throwable e
                          (log/warnf e "Error removing object"))))
          "__next__" (py-class/make-tuple-instance-fn
                      next-fn
                      ;;In this case we are explicitly taking care of all conversions
                      ;;to python and back so we do not ask for any converters.
                      {:arg-converter nil
                       :result-converter nil})})))))


(defn iterator-as-python
  [^Iterator jvm-data]
  (errors/when-not-errorf
   (instance? Iterator jvm-data)
   "Argument (%s) is not a java Iterator" (type jvm-data))
  (@iterator-type* (make-jvm-object-handle jvm-data)))


(defmethod py-proto/pyobject->jvm :jvm-iterator-as-python
  [pyobj opt]
  (py-self->jvm-obj pyobj))


(extend-protocol py-proto/PBridgeToPython
  ;;already bridged!
  Pointer
  (as-python [item opts] item)
  Boolean
  (as-python [item opts] (py-proto/->python item opts))
  Number
  (as-python [item opts] (py-proto/->python item opts))
  String
  (as-python [item opts] (py-proto/->python item opts))
  Keyword
  (as-python [item opts] (py-proto/->python item opts))
  Symbol
  (as-python [item opts] (py-proto/->python item opts))
  Object
  (as-python [item opts]
    (cond
      (instance? Map item)
      (map-as-python item)
      (dtype/reader? item)
      (list-as-python item)
      (instance? IFn item)
      (py-fn/make-tuple-fn item)
      (instance? Iterable item)
      (iterable-as-python item)
      (instance? Iterator item)
      (iterator-as-python item)
      :else
      (errors/throwf "Enable to bridge type %s" (type item)))))
