(ns libpython-clj.python.bridge
  "Bridging classes to allow python and java to intermix."
  (:require [libpython-clj.jna :as libpy]
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
              with-interpreter
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
              ->py-fn]]
            [libpython-clj.python.interop :as pyinterop
             :refer
             [expose-bridge-to-python!
              pybridge->bridge
              create-bridge-from-att-map]]
            [clojure.stacktrace :as st]
            [tech.jna :as jna]
            [tech.v2.tensor :as dtt]
            [tech.v2.datatype.casting :as casting]
            [tech.v2.datatype.functional :as dtype-fn]
            [tech.v2.datatype :as dtype]
            [tech.resource :as resource]
            [clojure.set :as c-set])
  (:import [java.util Map RandomAccess List Map$Entry Iterator]
           [clojure.lang IFn Symbol Keyword Seqable
            Fn]
           [tech.v2.datatype ObjectReader ObjectWriter ObjectMutable
            ObjectIter MutableRemove]
           [tech.v2.datatype.typed_buffer TypedBuffer]
           [tech.v2.tensor.impl Tensor]
           [com.sun.jna Pointer]
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
  "Bridge a python object into the jvm.  Attempts to build a jvm bridge that 'hides' the
  python type.  This bridge is lazy and noncaching so use it wisely; it may be better to
  just copy the type once into the JVM.  Bridging is recursive so any subtypes are also
  bridged if possible or represented by a hashmap of {:type :value} if not."
  [item & [options]]
  (if (or (not item)
          (= :none-type (python-type item)))
    nil
    (py-proto/as-jvm item options)))


(defn as-python
  "Bridge a jvm object into python"
  [item & [options]]
  (if (not item)
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


(defmacro bridge-pyobject
  [pyobj interpreter & body]
  `(let [pyobj# ~pyobj
         interpreter# ~interpreter]
     (reify
       libpy-base/PToPyObjectPtr
       (convertible-to-pyobject-ptr? [item#] true)
       (->py-object-ptr [item#] (jna/as-ptr pyobj#))
       py-proto/PPythonType
       (get-python-type [item]
         (with-interpreter interpreter#
           (py-proto/get-python-type pyobj#)))
       py-proto/PCopyToPython
       (->python [item# options#] pyobj#)
       py-proto/PBridgeToPython
       (as-python [item# options#] pyobj#)
       py-proto/PCopyToJVM
       (->jvm [item# options#] item#)
       py-proto/PBridgeToJVM
       (as-jvm [item# options#] item#)
       py-proto/PPyObject
       (dir [item#]
         (with-interpreter interpreter#
           (py-proto/dir pyobj#)))
       (has-attr? [item# item-name#]
         (with-interpreter interpreter#
           (py-proto/has-attr? pyobj# item-name#)))
       (get-attr [item# item-name#]
         (with-interpreter interpreter#
           (-> (py-proto/get-attr pyobj# item-name#)
               as-jvm)))
       (set-attr! [item# item-name# item-value#]
         (with-interpreter interpreter#
           (py-proto/set-attr! pyobj# item-name#
                               (as-python item-value#))))
       (callable? [item#]
         (with-interpreter interpreter#
           (py-proto/callable? pyobj#)))
       (has-item? [item# item-name#]
         (with-interpreter interpreter#
           (py-proto/has-item? pyobj# item-name#)))
       (get-item [item# item-name#]
         (with-interpreter interpreter#
           (-> (py-proto/get-item pyobj# item-name#)
               as-jvm)))
       (set-item! [item# item-name# item-value#]
         (with-interpreter interpreter#
           (py-proto/set-item! pyobj# item-name# (as-python item-value#))))
       py-proto/PPyAttMap
       (att-type-map [item#]
         (with-interpreter interpreter#
           (py-proto/att-type-map pyobj#)))
       py-proto/PyCall
       (do-call-fn [callable# arglist# kw-arg-map#]
         (-> (py-proto/do-call-fn pyobj# arglist# kw-arg-map#)
             (as-jvm)))
       ~@body)))


(defn as-tuple
  [item-seq]
  (->> (map as-python item-seq)
       (->py-tuple)))


(defn as-dict
  [map-data]
  (->> map-data
       (map (fn [[k v]]
              [(as-python k) (as-python v)]))
       (->py-dict)))


(defn- py-impl-call-as
  [att-name att-map arglist]
  (if-let [py-fn (get att-map att-name)]
    (-> (py-proto/do-call-fn py-fn (map as-python arglist) nil)
        as-jvm)
    (throw (UnsupportedOperationException.
            (format "Python object has no attribute: %s"
                    att-name)))))


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
                    (with-interpreter interpreter
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
              (->> (py-call "__iter__")
                   (map (juxt identity #(.get this %)))
                   (map (fn [[k v :as tuple]]
                          (reify Map$Entry
                            (getKey [this] k)
                            (getValue [this] v)
                            (hashCode [this] (.hashCode ^Object tuple))
                            (equals [this o]
                              (.equals ^Object tuple o))))))]
          (.iterator ^Iterable mapentry-seq)))
       IFn
       (invoke [this arg] (.get this arg))
       (invoke [this k v] (.put this k v))
       (applyTo [this arglist]
                (let [arglist (vec arglist)]
                  (case (count arglist)
                    1 (.get this (first arglist))
                    2 (.put this (first arglist) (second arglist)))))))))


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
                    (with-interpreter interpreter
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
  (if-let [retval (->> (py-proto/get-attr py-dtype "name")
                       (get py-dtype->dtype-map))]
    retval
    (throw (ex-info (format "Unable to find datatype: %s"
                            (py-proto/get-attr py-dtype "name"))
                    {}))))


(defn numpy->desc
  [np-obj]
  (with-gil
    (let [np-obj (as-jvm np-obj)
          np (-> (pyinterop/import-module "numpy")
                 (as-jvm))
          ctypes (get-attr np-obj "ctypes")
          ptr-dtype (-> (call-attr np "dtype" "p")
                        obj-dtype->dtype)
          obj-dtype (get-attr np-obj "dtype")
          np-dtype  (obj-dtype->dtype obj-dtype)
          _ (when-let [fields (get-attr obj-dtype "fields")]
              (throw (ex-info (format "Cannot convert numpy object with fields: %s"
                                      (call-attr fields "__str__"))
                              {})))
          shape (-> (get-attr ctypes "shape")
                    (as-list)
                    vec)
          strides (-> (get-attr ctypes "strides")
                    (as-list)
                    vec)
          long-addr (get-attr ctypes "data")
          hash-ary {:ctypes-map ctypes}
          ptr-val (-> (Pointer. long-addr)
                      (resource/track #(get hash-ary :ctypes-map) [:gc]))]
      {:ptr ptr-val
       :datatype np-dtype
       :shape shape
       :strides strides})))


(defn generic-python-as-jvm
  "Given a generic pyobject, wrap it in a read-only map interface
  where the keys are the attributes."
  [pyobj]
  (with-gil nil
    (if (= :none-type (python-type pyobj))
      nil
      (let [interpreter (ensure-bound-interpreter)]
        (if (py-proto/callable? pyobj)
          (bridge-pyobject
           pyobj
           interpreter
           Iterable
           (iterator [this]
                     (with-interpreter interpreter
                       (let [iter-fn (py-proto/get-attr pyobj "__iter__")]
                         (python->jvm-iterator iter-fn ->jvm))))
           py-proto/PPyObjectBridgeToMap
           (as-map [item]
                   (generic-python-as-map pyobj))
           py-proto/PPyObjectBridgeToList
           (as-list [item]
                    (generic-python-as-list pyobj))
           IFn
           ;;uggh
           (invoke [this]
                   (with-interpreter interpreter
                     (-> (libpy/PyObject_CallObject pyobj nil)
                         wrap-pyobject
                         as-jvm)))

           (invoke [this arg0]
                   (with-interpreter interpreter
                     (-> (libpy/PyObject_CallObject pyobj (as-tuple [arg0]))
                         wrap-pyobject
                         as-jvm)))

           (invoke [this arg0 arg1]
                   (with-interpreter interpreter
                     (-> (libpy/PyObject_CallObject pyobj (as-tuple [arg0 arg1]))
                         wrap-pyobject
                         as-jvm)))

           (invoke [this arg0 arg1 arg2]
                   (with-interpreter interpreter
                     (-> (libpy/PyObject_CallObject pyobj (as-tuple [arg0 arg1 arg2]))
                         wrap-pyobject
                         as-jvm)))

           (invoke [this arg0 arg1 arg2 arg3]
                   (with-interpreter interpreter
                     (-> (libpy/PyObject_CallObject
                          pyobj (as-tuple [arg0 arg1 arg2 arg3]))
                         wrap-pyobject
                         as-jvm)))


           (invoke [this arg0 arg1 arg2 arg3 arg4]
                   (with-interpreter interpreter
                     (-> (libpy/PyObject_CallObject
                          pyobj (as-tuple [arg0 arg1 arg2 arg3 arg4]))
                         wrap-pyobject
                         as-jvm)))

           (applyTo [this arglist]
                    (with-interpreter interpreter
                      (-> (libpy/PyObject_CallObject pyobj (as-tuple arglist))
                          wrap-pyobject
                          as-jvm)))
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
                     (with-interpreter interpreter
                       (let [iter-fn (py-proto/get-attr pyobj "__iter__")]
                         (python->jvm-iterator iter-fn ->jvm))))
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

(defmacro impl-tuple-function
  [& body]
  `(reify CFunction$TupleFunction
     (pyinvoke [this# ~'self ~'args]
       (wrap-jvm-context
        (-> (let [~'args (as-jvm ~'args)]
              ~@body)
            as-python)))))


(defn jvm-fn->iface
  [jvm-fn]
  (impl-tuple-function
   (apply jvm-fn args)))


(defn as-py-fn
  [jvm-fn]
  (-> (jvm-fn->iface jvm-fn)
      ->py-fn))


(defn jvm-iterator-as-python
  ^Pointer [^Iterator item]
  (with-gil nil
    (let [att-map
          {"__next__" (as-py-fn
                       #(when (.hasNext item)
                          (.next item)))}]
      (create-bridge-from-att-map item att-map))))


(defn jvm-map-as-python
  ^Pointer [^Map jvm-data]
  (with-gil
    (let [att-map
          {"__contains__" (as-py-fn #(.containsKey jvm-data %))
           "__eq__" (as-py-fn #(.equals jvm-data %))
           "__getitem__" (as-py-fn #(.get jvm-data %))
           "__setitem__" (as-py-fn #(.put jvm-data %1 %2))
           "__delitem__" (as-py-fn #(.remove jvm-data %))
           "__hash__" (as-py-fn #(.hashCode jvm-data))
           "__iter__" (as-py-fn #(.iterator ^Iterable jvm-data))
           "__len__" (as-py-fn #(.size jvm-data))
           "__str__" (as-py-fn #(.toString jvm-data))
           "clear" (as-py-fn #(.clear jvm-data))
           "keys" (as-py-fn #(seq (.keySet jvm-data)))
           "values" (as-py-fn #(seq (.values jvm-data)))
           "pop" (as-py-fn #(.remove jvm-data %))}]
      (create-bridge-from-att-map jvm-data att-map))))


(defn jvm-list-as-python
  ^Pointer [^List jvm-data]
  (with-gil
    (let [att-map
          {"__contains__" (as-py-fn #(.contains jvm-data %))
           "__eq__" (as-py-fn #(.equals jvm-data %))
           "__getitem__" (as-py-fn #(.get jvm-data (int %)))
           "__setitem__" (as-py-fn #(.set jvm-data (int %1) %2))
           "__delitem__" (as-py-fn #(.remove jvm-data (int %)))
           "__hash__" (as-py-fn #(.hashCode jvm-data))
           "__iter__" (as-py-fn #(.iterator jvm-data))
           "__len__" (as-py-fn #(.size jvm-data))
           "__str__" (as-py-fn #(.toString jvm-data))
           "clear" (as-py-fn #(.clear jvm-data))
           "sort" (as-py-fn #(.sort jvm-data nil))
           "append" (as-py-fn #(.add jvm-data %))
           "insert" (as-py-fn #(.add jvm-data (int %1) %2))
           "pop" (as-py-fn (fn [& args]
                                   (let [index (int (if (first args)
                                                      (first args)
                                                      -1))
                                         index (if (< index 0)
                                                 (- (.size jvm-data) index)
                                                 index)]
                                     #(.remove jvm-data index))))}]
      (create-bridge-from-att-map jvm-data att-map))))


(defn jvm-iterable-as-python
  ^Pointer [^Iterable jvm-data]
  (with-gil
    (let [att-map
          {"__iter__" (as-py-fn #(.iterator jvm-data))
           "__eq__" (as-py-fn #(.equals jvm-data %))
           "__hash__" (as-py-fn #(.hashCode jvm-data))
           "__str__" (as-py-fn #(.toString jvm-data))}]
      (create-bridge-from-att-map jvm-data att-map))))


(extend-protocol py-proto/PBridgeToPython
  Number
  (as-python [item options] (->python item))
  String
  (as-python [item options] (->python item))
  Symbol
  (as-python [item options] (->python item))
  Keyword
  (as-python [item options] (->python item))
  Boolean
  (as-python [item options] (->python item))
  Iterable
  (as-python [item options]
    (cond
      (instance? item Map)
      (jvm-map-as-python item)
      (instance? item RandomAccess)
      (jvm-list-as-python item)
      :else
      (jvm-iterable-as-python item)))
  TypedBuffer
  (as-python [item options]
    (py-proto/as-numpy item options))
  Tensor
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
      (throw (ex-info (format "Unable to convert objects of type: %s"
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
      (resource/track retval #(get buffer-desc :ptr) [:gc]))))


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
  [item & [options]]
  (py-proto/->numpy item options))


(defn as-numpy
  [item & [options]]
  (py-proto/as-numpy item options))
