(ns libpython-clj.python.bridge
  "Bridging classes to allow python and java to intermix."
  (:require [libpython-clj.jna :as libpy]
            [libpython-clj.jna.concrete.err :as err]
            [libpython-clj.jna.base :as libpy-base]
            [libpython-clj.python.protocols
             :refer [python-type
                     has-attr? get-attr call-attr
                     dir att-type-map
                     pyobject-as-jvm
                     as-list]
             :as py-proto]
            [libpython-clj.python.interpreter
             :refer
             [with-gil
              ensure-bound-interpreter
              check-error-throw
              initialize!]
             :as pyinterp]
            [libpython-clj.python.object
             :refer
             [->jvm
              ->python
              stringable?
              stringable
              incref
              incref-wrap-pyobject
              wrap-pyobject
              python->jvm-iterable
              python->jvm-iterator
              py-none
              ->py-list
              ->py-tuple
              ->py-dict
              ->py-string
              ->py-fn]
             :as pyobj]
            [libpython-clj.python.interop :as pyinterop
             :refer
             [expose-bridge-to-python!
              pybridge->bridge
              create-bridge-from-att-map]]
            [libpython-clj.python.gc :as pygc]
            [clojure.stacktrace :as st]
            [tech.jna :as jna]
            [tech.v2.tensor :as dtt]
            [tech.v2.datatype.casting :as casting]
            [tech.v2.datatype.functional :as dtype-fn]
            [tech.v2.datatype :as dtype]
            [clojure.set :as c-set]
            [clojure.tools.logging :as log])
  (:import [java.util Map RandomAccess List Map$Entry Iterator UUID]
           [java.util.concurrent ConcurrentHashMap ConcurrentLinkedQueue]
           [clojure.lang IFn Symbol Keyword Seqable
            Fn MapEntry Range LongRange]
           [tech.v2.datatype ObjectReader ObjectWriter ObjectMutable
            ObjectIter MutableRemove]
           [tech.v2.datatype.typed_buffer TypedBuffer]
           [tech.v2.tensor.protocols PTensor]
           [com.sun.jna Pointer]
           [tech.resource GCReference]
           [java.io Writer]
           [libpython_clj.jna JVMBridge
            CFunction$KeyWordFunction
            CFunction$TupleFunction
            CFunction$NoArgFunction
            PyFunction
            PyObject]))


(set! *warn-on-reflection* true)


(def bridgeable-python-type-set
  #{:list :dict :tuple :string :int :float})


(defn bridgeable-python-type?
  [pyobj]
  (with-gil
    (or (-> (python-type pyobj)
            bridgeable-python-type-set
            boolean)
        (has-attr? pyobj "__iter__")
        (has-attr? pyobj "__getitem__"))))


(defn bridgeable-jvm-type?
  [jvm-obj]
  (or (number? jvm-obj)
      (stringable? jvm-obj)
      (instance? jvm-obj RandomAccess)
      (instance? jvm-obj Map)
      (instance? jvm-obj Iterable)
      (fn? jvm-obj)))


(defn check-py-method-return
  [^long retval]
  (when-not (= 0 retval)
    (check-error-throw)))


(defn as-jvm
  "Bridge a python object into the jvm.  Attempts to build a jvm bridge that 'hides'
  the python type.  This bridge is lazy and noncaching so use it wisely; it may be
  better to just copy the type once into the JVM.  Bridging is recursive so any
  subtypes are also bridged if possible or represented by a hashmap of {:type
  :value} if not."
  [item & [options]]
  (if (or (not item)
          (= :none-type (python-type item)))
    nil
    (py-proto/as-jvm item options)))


(defn as-python
  "Bridge a jvm object into python"
  [item & [options]]
  (if (nil? item)
    (py-none)
    (py-proto/as-python item options)))


(extend-protocol py-proto/PBridgeToJVM
  Pointer
  (as-jvm [item options]
    (pyobject-as-jvm item))
  PyObject
  (as-jvm [item options]
    (pyobject-as-jvm (.getPointer item))))


;; These cannot be bridged.  Why would you, anyway?

(defmethod pyobject-as-jvm :int
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :float
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :int-8
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :uint-8
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :int-16
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :uint-16
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :int-32
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :uint-32
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :int-64
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :uint-64
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :float-64
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :float-32
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :str
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :bool
  [pyobj]
  (->jvm pyobj))


(defmethod pyobject-as-jvm :range
  [pyobj]
  (->jvm pyobj))



(defn mostly-copy-arg
  "This is the dirty boundary between the languages.  Copying as often faster
  for simple things but we have to be careful not to attempt a copy of things that
  are only iterable (and not random access)."
  [arg]
  (cond
    (libpy/as-pyobj arg)
    (libpy/as-pyobj arg)
    (dtype/reader? arg)
    (->python arg)
    (instance? Map arg)
    (->python arg)
    (instance? Iterable arg)
    (as-python arg)
    :else
    (as-python arg)))


(defmacro bridge-pyobject
  [pyobj interpreter & body]
  `(let [pyobj# ~pyobj
         interpreter# ~interpreter]
     (with-meta
       (reify
         libpy-base/PToPyObjectPtr
         (convertible-to-pyobject-ptr? [item#] true)
         (->py-object-ptr [item#] (libpy/as-pyobj pyobj#))
         py-proto/PPythonType
         (get-python-type [item]
           (with-gil
             (py-proto/get-python-type pyobj#)))
         py-proto/PCopyToPython
         (->python [item# options#] pyobj#)
         py-proto/PBridgeToPython
         (as-python [item# options#] pyobj#)
         py-proto/PBridgeToJVM
         (as-jvm [item# options#] item#)
         py-proto/PCopyToJVM
         (->jvm [item# options#]
           (with-gil
             (->jvm pyobj# options#)))
         py-proto/PPyObject
         (dir [item#]
           (with-gil
             (py-proto/dir pyobj#)))
         (has-attr? [item# item-name#]
           (with-gil
             (py-proto/has-attr? pyobj# item-name#)))
         (get-attr [item# item-name#]
           (with-gil
             (-> (py-proto/get-attr pyobj# item-name#)
                 as-jvm)))
         (set-attr! [item# item-name# item-value#]
           (with-gil
             (py-proto/set-attr! pyobj# item-name#
                                 (as-python item-value#))))
         (callable? [item#]
           (with-gil
             (py-proto/callable? pyobj#)))
         (has-item? [item# item-name#]
           (with-gil
             (py-proto/has-item? pyobj# item-name#)))
         (get-item [item# item-name#]
           (with-gil
             (-> (py-proto/get-item pyobj# item-name#)
                 as-jvm)))
         (set-item! [item# item-name# item-value#]
           (with-gil
             (py-proto/set-item! pyobj# item-name# (as-python item-value#))))
         py-proto/PPyAttMap
         (att-type-map [item#]
           (with-gil
             (py-proto/att-type-map pyobj#)))
         py-proto/PyCall
         (do-call-fn [callable# arglist# kw-arg-map#]
           (with-gil
             (let [arglist# (mapv mostly-copy-arg arglist#)
                   kw-arg-map# (->> kw-arg-map#
                                    (map (fn [[k# v#]]
                                           [k# (mostly-copy-arg v#)]))
                                    (into {}))]
               (-> (py-proto/do-call-fn pyobj# arglist# kw-arg-map#)
                   (as-jvm)))))
         Object
         (toString [this#]
           (with-gil
             (if (= 1 (libpy/PyObject_IsInstance pyobj# (libpy/PyType_Type)))
               (format "%s.%s"
                       (->jvm (py-proto/get-attr pyobj# "__module__"))
                       (->jvm (py-proto/get-attr pyobj# "__name__")))
               (->jvm (py-proto/call-attr pyobj# "__str__")))))
         (equals [this# other#]
           (pyobj/equals? this# other#))
         (hashCode [this#]
           (.hashCode ^Object (pyobj/hash-code this#)))
         ~@body)
       {:type :pyobject})))


(defmethod print-method :pyobject
  [pyobj w]
  (.write ^Writer w ^String (.toString ^Object pyobj)))


(defn as-tuple
  "Create a python tuple from a sequence of things."
  [item-seq]
  (->> (map as-python item-seq)
       (->py-tuple)))


(defn as-dict
  "Create a python dict from a sequence of things."
  [map-data]
  (->> map-data
       (map (fn [[k v]]
              [(as-python k) (as-python v)]))
       (->py-dict)))



(defn- py-impl-call-raw
  [att-name att-map arglist]
  (if-let [py-fn (get att-map att-name)]
    (py-proto/do-call-fn py-fn (map as-python arglist) nil)
    (throw (UnsupportedOperationException.
            (format "Python object has no attribute: %s"
                    att-name)))))


(defn- py-impl-call-as
  [att-name att-map arglist]
  (-> (py-impl-call-raw att-name att-map arglist)
      as-jvm))

(defn- raw-python-iterator
  [att-map]
  (when-not (get att-map "__iter__")
    (throw (ex-info "Object is not iterable!" {})))
  (let [py-iter (python->jvm-iterator (get att-map "__iter__") identity)]
    py-iter))

(defn generic-python-as-map
  [pyobj]
  (with-gil
    (let [interpreter (ensure-bound-interpreter)
          dict-atts #{"__len__" "__getitem__" "__setitem__" "__iter__" "__contains__"
                      "__eq__" "__hash__" "clear" "keys" "values"
                      "__delitem__"}
          dict-att-map (->> (py-proto/dir pyobj)
                            (filter dict-atts)
                            (map (juxt identity (partial py-proto/get-attr pyobj)))
                            (into {}))
          py-call (fn [fn-name & args]
                    (with-gil
                      (py-impl-call-as fn-name dict-att-map args)))]

      (bridge-pyobject
       pyobj
       interpreter
       Map
       (clear [item] (py-call "clear"))
       (containsKey [item k] (boolean (py-call "__contains__" k)))
       (entrySet
        [this]
        (->> (.iterator this)
             iterator-seq
             set))
       (get [this obj-key]
            (py-call "__getitem__" obj-key))
       (getOrDefault [item obj-key obj-default-value]
                     (if (.containsKey item obj-key)
                       (.get item obj-key)
                       obj-default-value))
       (isEmpty [this] (= 0 (.size this)))
       (keySet [this] (->> (py-call "keys")
                           set))
       (put [this k v]
            (py-call "__setitem__" k v))

       (remove [this k]
               (py-call "__delitem__" k))

       (size [this]
             (int (py-call "__len__")))


       (values [this]
               (py-call "values"))
       Iterable
       (iterator
        [this]
        (let [mapentry-seq
              (->> (raw-python-iterator dict-att-map)
                   iterator-seq
                   (map (fn [pyobj-key]
                          (with-gil
                            (let [k (as-jvm pyobj-key)
                                  v (.get this pyobj-key)
                                  tuple [k v]]
                              (MapEntry. k v))))))]
          (.iterator ^Iterable mapentry-seq)))
       IFn
       (invoke [this arg] (.getOrDefault this arg nil))
       (invoke [this k v] (.put this k v))
       (applyTo [this arglist]
                (let [arglist (vec arglist)]
                  (case (count arglist)
                    1 (.get this (first arglist))
                    2 (.put this (first arglist) (second arglist)))))
       py-proto/PPyObjectBridgeToMap
       (as-map [item] item)))))


(defn generic-python-as-list
  [pyobj]
  (with-gil
    (let [interpreter (ensure-bound-interpreter)
          dict-atts #{"__len__" "__getitem__" "__setitem__" "__iter__" "__contains__"
                      "__eq__" "__hash__" "clear" "insert" "pop" "append"
                      "__delitem__" "sort"}
          dict-att-map (->> (py-proto/dir pyobj)
                            (filter dict-atts)
                            (map (juxt identity (partial py-proto/get-attr pyobj)))
                            (into {}))
          py-call (fn [fn-name & args]
                    (with-gil
                      (py-impl-call-as fn-name dict-att-map args)))]
      (bridge-pyobject
       pyobj
       interpreter
       ObjectReader
       (lsize [reader]
              (long (py-call "__len__")))
       (read [reader idx]
             (py-call "__getitem__" idx))
       (sort [reader obj-com]
             (when-not (= nil obj-com)
               (throw (ex-info "Python lists do not support comparators" {})))
             (py-call "sort"))
       ObjectWriter
       (write [writer idx value]
              (py-call "__setitem__" idx value))
       (remove [writer ^int idx]
               (py-call "__delitem__" idx))
       ObjectMutable
       (insert [mutable idx value]
               (py-call "insert" idx value))
       (append [mutable value]
               (py-call "append" value))
       MutableRemove
       (mremove [mutable idx]
                (py-call "__delitem__" idx))))))



(defmethod pyobject-as-jvm :list
  [pyobj]
  (generic-python-as-list pyobj))


(defmethod pyobject-as-jvm :tuple
  [pyobj]
  (generic-python-as-list pyobj))


(defn check-pybool-return
  [^long retval]
  (cond
    (> retval 0) true
    (= retval 0) false
    :else
    (check-error-throw)))


(defmethod pyobject-as-jvm :dict
  [pyobj]
  (generic-python-as-map pyobj))


(defn python-iterable-as-jvm
  [pyobj]
  (python->jvm-iterable pyobj ->jvm))


(def py-dtype->dtype-map
  (->> (concat (for [bit-width [8 16 32 64]
                     unsigned? [true false]]
                 (str (if unsigned?
                        "uint"
                        "int")
                      bit-width))
               ["float32" "float64"])
       (map (juxt identity keyword))
       (into {})))


(def dtype->py-dtype-map
  (c-set/map-invert py-dtype->dtype-map))


(defn obj-dtype->dtype
  [py-dtype]
  (when-let [fields (get-attr py-dtype "fields")]
    (throw (ex-info (format "Cannot convert numpy object with fields: %s"
                            (call-attr fields "__str__"))
                    {})))
  (if-let [retval (->> (py-proto/get-attr py-dtype "name")
                       (get py-dtype->dtype-map))]
    retval
    (throw (ex-info (format "Unable to find datatype: %s"
                            (py-proto/get-attr py-dtype "name"))
                    {}))))


(defn numpy->desc
  [np-obj]
  (with-gil
    (let [np (pyinterop/import-module "numpy")
          ctypes (py-proto/as-jvm (get-attr np-obj "ctypes") {})
          np-dtype (-> (py-proto/as-jvm (get-attr np-obj "dtype") {})
                       (obj-dtype->dtype))
          shape (-> (get-attr ctypes "shape")
                    (as-list)
                    vec)
          strides (-> (get-attr ctypes "strides")
                    (as-list)
                    vec)
          long-addr (get-attr ctypes "data")
          hash-ary {:ctypes-map ctypes}
          ptr-val (-> (Pointer. long-addr)
                      (pygc/track #(get hash-ary :ctypes-map)))]
      {:ptr ptr-val
       :datatype np-dtype
       :shape shape
       :strides strides})))


(defmethod py-proto/python-obj-iterator :default
  [pyobj interpreter]
  (with-gil
    (let [iter-fn (py-proto/get-attr pyobj "__iter__")]
      (python->jvm-iterator iter-fn as-jvm))))


(defn args->pos-kw-args
  "Utility function that, given a list of arguments, separates them
  into positional and keyword arguments.  Throws an exception if the
  keyword argument is not followed by any more arguments."
  [arglist]
  (loop [args arglist
         pos-args []
         kw-args nil
         found-kw? false]
    (if-not (seq args)
      [pos-args kw-args]
      (let [arg (first args)
            [pos-args kw-args args found-kw?]
            (if (keyword? arg)
              (if-not (seq (rest args))
                (throw (Exception.
                        (format "Keyword arguments must be followed by another arg: %s"
                                (str arglist))))
                [pos-args (assoc kw-args arg (first (rest args)))
                 (drop 2 args) true])
              (if found-kw?
                (throw (Exception.
                        (format "Positional arguments are not allowed after keyword arguments: %s"
                                arglist)))
                [(conj pos-args (first args))
                 kw-args
                 (rest args) found-kw?]))]
        (recur args pos-args kw-args found-kw?)))))


(defn cfn
  "Call an object.
  Arguments are passed in positionally.  Any keyword
  arguments are paired with the next arg, gathered, and passed into the
  system as *kwargs.

  Not having an argument after a keyword argument is an error."
  [item & args]
  (let [[pos-args kw-args] (args->pos-kw-args args)]
    (py-proto/call-kw item pos-args kw-args)))


(defn key-sym-str->str
  [attr-name]
  (cond
    (or (keyword? attr-name)
        (symbol? attr-name))
    (name attr-name)
    (string? attr-name)
    attr-name
    :else
    (throw (Exception.
            "Only keywords, symbols, or strings can be used to access attributes."))))


(defn afn
  "Call an attribute of an object.
  Arguments are passed in positionally.  Any keyword
  arguments are paired with the next arg, gathered, and passed into the
  system as *kwargs.

  Not having an argument after a keyword argument is an error."
  [item attr & args]
  (let [[pos-args kw-args] (args->pos-kw-args args)]
    (py-proto/call-attr-kw item (key-sym-str->str attr)
                           pos-args kw-args)))

;;utility fn to generate IFn arities
(defn- emit-args
  [bodyf varf]
   (let [argify (fn [n argfn bodyf]
                  (let [raw  `[~'this ~@(map #(symbol (str "arg" %))
                                             (range n))]]
                    `~(bodyf (argfn raw))))]
     (concat (for [i (range 21)]
               (argify i identity bodyf))
             [(argify 21 (fn [xs]
                          `[~@(butlast xs) ~'arg20-obj-array])
                     varf)])))

;;Python specific interop wrapper for IFn invocations.
(defn- emit-py-args []
  (emit-args    (fn [args] `(~'invoke [~@args]
                             (~'with-gil
                              (~'cfn ~@args))))
                (fn [args]
                  `(~'invoke [~@args]
                    (~'with-gil
                     (~'apply ~'cfn ~@(butlast args) ~(last args)))))))

(defmacro bridge-callable-pyobject
  "Like bridge-pyobject, except it populates the implementation of IFn
   for us, where all arg permutations are supplied, as well as applyTo,
   and the function invocation is of the form
   (invoke [this arg] (with-gil (cfn this arg))).
   If caller supplies an implementation for clojure.lang.IFn or aliased
   Fn, the macro will use that instead (allowing more control but
   requiring caller to specify implementations for all desired arities)."
  [pyobj interpreter & body]
  (let [fn-specs (when-not (some #{'IFn 'clojure.lang.IFn} body)
                   `(~'IFn
                     ~@(emit-py-args)
                     (~'applyTo [~'this ~'arglist]
                      (~'with-gil
                       (~'apply ~'cfn ~'this ~'arglist)))))]
    `(bridge-pyobject ~pyobj ~interpreter
                      ~@fn-specs
                      ~@body)))

(defn generic-python-as-jvm
  "Given a generic pyobject, wrap it in a read-only map interface
  where the keys are the attributes."
  [pyobj]
  (with-gil
    (if (= :none-type (python-type pyobj))
      nil
      (let [interpreter (ensure-bound-interpreter)]
        (if (py-proto/callable? pyobj)
          (bridge-callable-pyobject
           pyobj
           interpreter
           Iterable
           (iterator [this]
                     (py-proto/python-obj-iterator pyobj interpreter))
           py-proto/PPyObjectBridgeToMap
           (as-map [item]
                   (generic-python-as-map pyobj))
           py-proto/PPyObjectBridgeToList
           (as-list [item]
                    (generic-python-as-list pyobj))
           ;;IFn is now supplied for us by the macro.
           ;;Mark this as executable
           Fn
           PyFunction
           (invokeKeyWords [this tuple-args keyword-args]
                           (-> (libpy/PyObject_Call pyobj
                                                    (as-tuple tuple-args)
                                                    (->py-dict keyword-args))
                               wrap-pyobject
                               as-jvm)))
          (bridge-pyobject
           pyobj
           interpreter
           Iterable
           (iterator [this]
                     (py-proto/python-obj-iterator pyobj interpreter))
           py-proto/PPyObjectBridgeToMap
           (as-map [item]
                   (generic-python-as-map pyobj))
           py-proto/PPyObjectBridgeToList
           (as-list [item]
                    (generic-python-as-list pyobj))
           py-proto/PPyObjectBridgeToTensor
           (as-tensor [item]
                      (-> (numpy->desc item)
                          dtt/buffer-descriptor->tensor))))))))


(defmethod pyobject-as-jvm :default
  [pyobj]
  (generic-python-as-jvm pyobj))

(defmacro wrap-jvm-context
  [& body]
  `(try
     ~@body
     (catch Throwable e#
       (libpy/PyErr_SetString
        libpy/PyExc_Exception
        (format "%s:%s" (str e#)
                (with-out-str
                  (st/print-stack-trace e#)))))))


(defn as-python-incref
  "Convert to python and add a reference.  Necessary for return values from
  functions as python expects a new reference and the as-python pathway
  ensures the jvm garbage collector also sees the reference."
  [item]
  (-> (as-python item)
      (pyobj/incref)))


(defn- as-py-fn
  ^Pointer [obj-fn]
  (pyobj/make-tuple-fn obj-fn
                       :arg-converter as-jvm
                       :result-converter as-python-incref))


(defonce ^:private jvm-handle-map (ConcurrentHashMap.))

(defn- make-jvm-object-handle
  ^long [item]
  (let [^ConcurrentHashMap hash-map jvm-handle-map]
    (loop [handle (pyinterp/get-object-handle item)]
      (let [handle (long handle)]
        (if (not (.containsKey hash-map handle))
          (do
            (.put hash-map handle item)
            handle)
          (recur (.hashCode (UUID/randomUUID))))))))

(defn- get-jvm-object
  [handle]
  (.get ^ConcurrentHashMap jvm-handle-map (long handle)))

(defn- remove-jvm-object
  [handle]
  (.remove ^ConcurrentHashMap jvm-handle-map (long handle))
  nil)

(defn- py-self->jvm-handle
  [self]
  (->jvm (py-proto/get-attr self "jvm_handle")))

(defn- py-self->jvm-obj
  ^Object [self]
  (-> (py-self->jvm-handle self)
      get-jvm-object))

(defn- as-tuple-instance-fn
  [fn-obj]
  (pyobj/make-tuple-instance-fn fn-obj :result-converter as-python-incref))


(defn- self->map
  ^Map [self]
  (py-self->jvm-obj self))


(defmacro pydelay
  "Create a delay object that uses only gc reference semantics.  If stack reference
  semantics happen to be in effect when this delay executes the object may still be
  reachable by your program when it's reference counts are released leading to
  bad/crashy behavior.  This ensures that can't happen at the cost of possibly an object
  sticking around."
  [& body]
  `(delay
     (with-gil
       (with-bindings {#'pygc/*stack-gc-context* nil}
         ~@body))))


(defonce mapping-type
  (pydelay
    (with-gil
      (let [mod (pyinterop/import-module "collections.abc")
            map-type (py-proto/get-attr mod "MutableMapping")]
        ;;In order to make things work ingeneral
        (pyobj/create-class
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
          "__contains__" (as-tuple-instance-fn #(.containsKey (self->map %1) (as-jvm %2)))
          "__eq__" (as-tuple-instance-fn #(.equals (self->map %1) (as-jvm %2)))
          "__getitem__" (as-tuple-instance-fn
                         #(do (println "getting" (as-jvm %2))
                              (.get (self->map %1) (as-jvm %2))))
          "__setitem__" (as-tuple-instance-fn #(.put (self->map %1) (as-jvm %2) %3))
          "__delitem__" (as-tuple-instance-fn #(.remove (self->map %1) (as-jvm %2)))
          "__hash__" (as-tuple-instance-fn #(.hashCode (self->map %1)))
          "__iter__" (as-tuple-instance-fn #(.iterator ^Iterable (keys (self->map %1))))
          "__len__" (as-tuple-instance-fn #(.size (self->map %1)))
          "__str__" (as-tuple-instance-fn #(.toString (self->map %1)))
          "clear" (as-tuple-instance-fn #(.clear (self->map %1)))
          "keys" (as-tuple-instance-fn #(seq (.keySet (self->map %1))))
          "values" (as-tuple-instance-fn #(seq (.values (self->map %1))))
          "pop" (as-tuple-instance-fn #(.remove (self->map %1) (as-jvm %2)))})))))


(defn jvm-map-as-python
  [^Map jvm-data]
  (with-gil
    (py-proto/call (libpy/as-pyobj (deref mapping-type)) (make-jvm-object-handle jvm-data))))


(defmethod py-proto/pyobject->jvm :jvm-map-as-python
  [pyobj]
  (py-self->jvm-obj pyobj))


(defmethod pyobject-as-jvm :jvm-map-as-python
  [pyobj]
  (->jvm pyobj))


(defn- self->list
  ^List [self]
  (py-self->jvm-obj self))

(defonce sequence-type
  (pydelay
    (let [mod (pyinterop/import-module "collections.abc")
          seq-type (py-proto/get-attr mod "MutableSequence")]
      (pyobj/create-class
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
        "__eq__" (as-tuple-instance-fn #(.equals (self->list %1) (->jvm %2)))
        "__getitem__" (as-tuple-instance-fn #(.get (self->list %1) (int (->jvm %2))))
        "__setitem__" (as-tuple-instance-fn #(.set (self->list %1)
                                                   (int (->jvm %2))
                                                   (as-jvm %3)))
        "__delitem__" (as-tuple-instance-fn #(.remove (self->list %1)
                                                      (int (->jvm %2))))
        "__hash__" (as-tuple-instance-fn #(.hashCode (self->list %1)))
        "__iter__" (as-tuple-instance-fn #(.iterator (self->list %1)))
        "__len__" (as-tuple-instance-fn #(.size (self->list %1)))
        "__str__" (as-tuple-instance-fn #(.toString (self->list %1)))
        "clear" (as-tuple-instance-fn #(.clear (self->list %1)))
        "sort" (as-tuple-instance-fn #(.sort (self->list %1) nil))
        "append" (as-tuple-instance-fn #(.add (self->list %1) %2))
        "insert" (as-tuple-instance-fn #(.add (self->list %1) (int (->jvm %2)) %3))
        "pop" (as-tuple-instance-fn
               (fn [self & args]
                 (let [jvm-data (self->list self)
                       args (map ->jvm args)
                       index (int (if (first args)
                                     (first args)
                                     -1))
                       index (if (< index 0)
                               (- (.size jvm-data) index)
                               index)]
                   #(.remove jvm-data index))))}))))


(defn jvm-list-as-python
  [^List jvm-data]
  (with-gil
    (py-proto/call (libpy/as-pyobj (deref sequence-type)) (make-jvm-object-handle jvm-data))))


(defmethod py-proto/pyobject->jvm :jvm-list-as-python
  [pyobj]
  (py-self->jvm-obj pyobj))


(defmethod pyobject-as-jvm :jvm-list-as-python
  [pyobj]
  (->jvm pyobj))


(defonce iterable-type
  (pydelay
    (with-gil
      (let [mod (pyinterop/import-module "collections.abc")
            iter-base-cls (py-proto/get-attr mod "Iterable")]
        (pyobj/create-class
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
                      #(.iterator ^Iterable (py-self->jvm-obj %)))
          "__eq__" (as-tuple-instance-fn #(.equals (py-self->jvm-obj %1)
                                                   (as-jvm %2)))
          "__hash__" (as-tuple-instance-fn
                      #(.hashCode (py-self->jvm-obj %)))
          "__str__" (as-tuple-instance-fn
                     #(.toString (py-self->jvm-obj %)))})))))


(defn jvm-iterable-as-python
  [^Iterable jvm-data]
  (with-gil
    (py-proto/call (libpy/as-pyobj (deref iterable-type)) (make-jvm-object-handle jvm-data))))


(defmethod py-proto/pyobject->jvm :jvm-iterable-as-python
  [pyobj]
  (py-self->jvm-obj pyobj))


(defmethod pyobject-as-jvm :jvm-iterable-as-python
  [pyobj]
  (->jvm pyobj))


(defonce iterator-type
  (pydelay
    (with-gil
      (let [mod (pyinterop/import-module "collections.abc")
            iter-base-cls (py-proto/get-attr mod "Iterator")
            next-fn (fn [self]
                      (let [^Iterator item (py-self->jvm-obj self)]
                        (if (.hasNext item)
                          (-> (.next item)
                              ;;As python tracks the object in a jvm context
                              (as-python)
                              (jna/->ptr-backing-store)
                              ;;But we are handing the object back to python which is expecting
                              ;;a new reference.
                              (pyobj/incref))
                          (do
                            (libpy/PyErr_SetNone
                             (err/PyExc_StopIteration))
                            nil))))]
        (pyobj/create-class
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
          "__next__" (pyobj/make-tuple-instance-fn
                      next-fn
                      ;;In this case we are explicitly taking care of all conversions
                      ;;to python and back so we do not ask for any converters.
                      :arg-converter nil
                      :result-converter nil)})))))


(defn jvm-iterator-as-python
  [^Iterator jvm-data]
  (with-gil
    (py-proto/call (libpy/as-pyobj (deref iterator-type)) (make-jvm-object-handle jvm-data))))


(defmethod py-proto/pyobject->jvm :jvm-iterator-as-python
  [pyobj]
  (py-self->jvm-obj pyobj))


(defmethod pyobject-as-jvm :jvm-iterator-as-python
  [pyobj]
  (->jvm pyobj))

(extend-protocol py-proto/PBridgeToPython
  Number
  (as-python [item options] (->python item))
  String
  (as-python [item options] (->python item))
  Character
  (as-python [item options] (->python item))
  Symbol
  (as-python [item options] (->python item))
  Keyword
  (as-python [item options] (->python item))
  Boolean
  (as-python [item options] (->python item))
  Range
  (as-python [item options]
    (if (casting/integer-type? (dtype/get-datatype item))
      (->python item options)
      (jvm-iterable-as-python item)))
  LongRange
  (as-python [item options]
    (->python item options))
  Iterable
  (as-python [item options]
    (cond
      (instance? Map item)
      (jvm-map-as-python item)
      (instance? RandomAccess item)
      (jvm-list-as-python item)
      :else
      (jvm-iterable-as-python item)))
  TypedBuffer
  (as-python [item options]
    (py-proto/as-numpy item options))
  PTensor
  (as-python [item options]
    (py-proto/as-numpy item options))
  Iterator
  (as-python [item options]
    (jvm-iterator-as-python item))
  Object
  (as-python [item options]
    (cond
      (fn? item)
      (as-py-fn item)
      :else
      (throw (Exception. (format "Unable to convert objects of type: %s"
                                 (type item)))))))


(defn datatype->ptr-type-name
  [dtype]
  (case dtype
    :int8 "c_byte"
    :uint8 "c_ubyte"
    :int16 "c_short"
    :uint16 "c_ushort"
    :int32 "c_long"
    :uint32 "c_ulong"
    :int64 "c_longlong"
    :uint64 "c_ulonglong"
    :float32 "c_float"
    :float64 "c_double"))


(defn descriptor->numpy
  [{:keys [ptr shape strides datatype] :as buffer-desc}]
  (with-gil
    (let [stride-tricks (-> (pyinterop/import-module "numpy.lib.stride_tricks")
                            as-jvm)
          ctypes (-> (pyinterop/import-module "ctypes")
                     as-jvm)
          np-ctypes (-> (pyinterop/import-module "numpy.ctypeslib")
                        as-jvm)
          dtype-size (casting/numeric-byte-width datatype)
          max-stride-idx (dtype-fn/argmax strides)
          buffer-len (* (long (dtype/get-value shape max-stride-idx))
                        (long (dtype/get-value strides max-stride-idx)))
          n-elems (quot buffer-len dtype-size)
          lvalue (Pointer/nativeValue ^Pointer ptr)
          void-p (call-attr ctypes "c_void_p" lvalue)
          actual-ptr (call-attr
                      ctypes "cast" void-p
                      (call-attr
                       ctypes "POINTER"
                       (py-proto/get-attr ctypes
                                          (datatype->ptr-type-name datatype))))

          initial-buffer (call-attr
                          np-ctypes "as_array"
                          actual-ptr (->py-tuple [n-elems]))

          retval (call-attr stride-tricks "as_strided"
                            initial-buffer
                            (->py-tuple shape)
                            (->py-tuple strides))]
      (pygc/track retval #(get buffer-desc :ptr)))))


(extend-type Object
  py-proto/PJvmToNumpy
  (->numpy [item options]
    (-> (dtt/ensure-buffer-descriptor item)
        descriptor->numpy))
  py-proto/PJvmToNumpyBridge
  (as-numpy [item options]
    (when-let [desc (-> (dtt/ensure-tensor item)
                        dtype/as-buffer-descriptor)]
      (descriptor->numpy desc))))


(defn ->numpy
  "Convert an object to numpy throwing an error if this isn't possible."
  [item & [options]]
  (py-proto/->numpy item options))


(defn as-numpy
  "Bridge an object into numpy sharing the backing store.  If it is not possible to
  do this without copying data then return nil."
  [item & [options]]
  (py-proto/as-numpy item options))
