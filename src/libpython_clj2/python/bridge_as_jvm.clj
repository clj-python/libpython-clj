(ns libpython-clj2.python.bridge-as-jvm
  (:require [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.fn :as py-fn]
            [libpython-clj2.python.ffi :refer [with-gil] :as py-ffi]
            [libpython-clj.python.gc :as pygc]
            [tech.v3.datatype.errors :as errors]
            [tech.v3.datatype.ffi :as dt-ffi]
            [clojure.core.protocols :as clj-proto])
  (:import [java.util Map]
           [clojure.lang IFn MapEntry Fn]
           [tech.v3.datatype.ffi Pointer]
           [tech.v3.datatype ObjectBuffer]))


(extend-protocol py-proto/PBridgeToJVM
  Pointer
  (as-jvm [ptr opts]
    (py-proto/pyobject-as-jvm ptr opts)))


(defmethod py-proto/pyobject-as-jvm :int
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :float
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :int-8
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :uint-8
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :int-16
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :uint-16
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :int-32
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :uint-32
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :int-64
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :uint-64
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :float-64
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :float-32
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :str
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :bool
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defmethod py-proto/pyobject-as-jvm :range
  [pyobj & [opts]]
  (py-base/->jvm pyobj opts))


(defn python->jvm-iterator
  "This is a tough function to get right.  The iterator could return nil as in
  you could have a list of python none types or something so you have to iterate
  till you get a StopIteration error."
  [iter-fn & [item-conversion-fn]]
  (with-gil
    (let [py-iter (py-fn/call iter-fn)
          py-next-fn (when py-iter (py-proto/get-attr py-iter "__next__"))
          next-fn (fn [last-item]
                    (when (= last-item ::iteration-finished)
                      (errors/throw-iterator-past-end))
                    (with-gil
                      (let [retval (py-ffi/PyObject_CallObject py-next-fn nil)]
                        (if (and (nil? retval) (py-ffi/PyErr_Occurred))
                          (pygc/with-stack-context
                            (let [type (dt-ffi/make-ptr :pointer 0)
                                  value (dt-ffi/make-ptr :pointer 0)
                                  tb (dt-ffi/make-ptr :pointer 0)
                                  _ (py-ffi/PyErr_Fetch type value tb)]
                              (if (= (Pointer. (type 0)) (py-ffi/py-exc-stopiter-type))
                                (do
                                  (py-ffi/Py_DecRef (Pointer. (type 0)))
                                  (when-not (== 0 (long (value 0)))
                                    (py-ffi/Py_DecRef (Pointer. (value 0))))
                                  (when-not (== 0 (long (tb 0)))
                                    (py-ffi/Py_DecRef (Pointer. (tb 0))))
                                  ::iteration-finished)
                                (do
                                  (py-ffi/PyErr_Restore (Pointer. (type 0))
                                                        (Pointer. (value 0))
                                                        (Pointer. (tb 0)))
                                  (py-ffi/check-error-throw)))))
                          [(cond-> (py-ffi/wrap-pyobject retval)
                             item-conversion-fn
                             item-conversion-fn)]))))
          cur-item-store (atom (next-fn nil))]
      (reify
        java.util.Iterator
        (hasNext [obj-iter]
          (boolean (not= ::iteration-finished @cur-item-store)))
        (next [obj-iter]
          (-> (swap-vals! cur-item-store next-fn)
              ffirst))))))


(defn- raw-python-iterator
  [att-map]
  (when-not (get att-map "__iter__")
    (throw (ex-info "Object is not iterable!" {})))
  (let [py-iter (python->jvm-iterator (get att-map "__iter__") identity)]
    py-iter))


(defmacro bridge-pyobject
  [pyobj & body]
  `(let [pyobj# ~pyobj]
     (with-meta
       (reify
         dt-ffi/PToPointer
         (convertible-to-pointer? [item#] true)
         (->pointer [item#] (dt-ffi/->pointer pyobj#))
         py-proto/PPythonType
         (get-python-type [item]
           (with-gil (py-proto/get-python-type pyobj#)))
         py-proto/PCopyToPython
         (py-base/->python [item# options#] pyobj#)
         py-proto/PBridgeToPython
         (py-base/as-python [item# options#] pyobj#)
         py-proto/PBridgeToJVM
         (py-base/as-jvm [item# options#] item#)
         py-proto/PCopyToJVM
         (->jvm [item# options#]
           (with-gil
             (py-base/->jvm pyobj# options#)))
         py-proto/PPyDir
         (dir [item#]
           (with-gil (py-proto/dir pyobj#)))
         py-proto/PPyAttr
         (has-attr? [item# item-name#]
           (with-gil
             (py-proto/has-attr? pyobj# item-name#)))
         (get-attr [item# item-name#]
           (with-gil
             (-> (py-proto/get-attr pyobj# item-name#)
                 py-base/as-jvm)))
         (set-attr! [item# item-name# item-value#]
           (with-gil
             (py-proto/set-attr! pyobj# item-name#
                                 (py-base/as-python item-value#))))
         py-proto/PPyItem
         (has-item? [item# item-name#]
           (with-gil
             (py-proto/has-item? pyobj# item-name#)))
         (get-item [item# item-name#]
           (with-gil
             (-> (py-proto/get-item pyobj# item-name#)
                 py-base/as-jvm)))
         (set-item! [item# item-name# item-value#]
           (with-gil
             (py-proto/set-item! pyobj# item-name# (py-base/as-python item-value#))))
         py-proto/PyCall
         (call [callable# arglist# kw-arg-map#]
           (with-gil
             (-> (py-fn/call-kw pyobj# arglist# kw-arg-map#)
                 (py-base/as-jvm))))
         clj-proto/Datafiable
         (datafy [callable#] (py-proto/pydatafy callable#))
         Object
         (toString [this#]
           (with-gil
             (pygc/with-stack-context
               (if (= 1 (py-ffi/PyObject_IsInstance pyobj# (py-ffi/py-type-type)))
                 (format "%s.%s"
                         (if (py-proto/has-attr? pyobj# "__module__")
                           (py-base/->jvm (py-proto/get-attr pyobj# "__module__"))
                           "__no_module__")
                         (if (py-proto/has-attr? pyobj# "__name__")
                           (py-base/->jvm (py-proto/get-attr pyobj# "__name__"))
                           "__unnamed__"))
                 (py-base/->jvm (py-fn/call-attr pyobj# "__str__" nil))))))
         (equals [this# other#]
           (boolean
            (when (dt-ffi/convertible-to-pointer? other#)
              (py-base/equals? pyobj# other#))))
         (hashCode [this#]
           (.hashCode ^Object (py-base/hash-code this#)))
         ~@body)
       {:type :pyobject})))


(defmethod print-method :pyobject
  [pyobj w]
  (.write ^java.io.Writer w ^String (.toString ^Object pyobj)))


(defn call-impl-fn
  [fn-name att-map args]
  (if-let [py-fn (get att-map fn-name)]
    (-> (py-fn/call-kw py-fn (map py-base/as-python args) nil)
        (py-base/as-jvm))
    (throw (UnsupportedOperationException.
            (format "Python object has no attribute: %s"
                    fn-name)))))


(defn generic-python-as-map
  [pyobj]
  (with-gil
    (let [dict-atts #{"__len__" "__getitem__" "__setitem__" "__iter__" "__contains__"
                      "__eq__" "__hash__" "clear" "keys" "values"
                      "__delitem__"}
          dict-att-map (->> (py-proto/dir pyobj)
                            (filter dict-atts)
                            (map (juxt identity (partial py-proto/get-attr pyobj)))
                            (into {}))
          py-call (fn [fn-name & args]
                    (with-gil (call-impl-fn fn-name dict-att-map args)))]

      (bridge-pyobject
       pyobj
       Map
       (clear [item] (py-call "clear"))
       (containsKey [item k] (boolean (py-call "__contains__" k)))
       (entrySet
        [this]
        (py-ffi/with-gil
          (->> (.iterator this)
               iterator-seq
               set)))
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
               (py-ffi/with-gil
                 (-> (py-call "values")
                     (vec))))
       Iterable
       (iterator
        [this]
        (let [mapentry-seq
              (->> (raw-python-iterator dict-att-map)
                   iterator-seq
                   (map (fn [pyobj-key]
                          (with-gil
                            (let [k (py-base/as-jvm pyobj-key)
                                  v (.get this pyobj-key)]
                              (MapEntry. k v))))))]
          (.iterator ^Iterable mapentry-seq)))
       IFn
       (invoke [this arg] (.getOrDefault this arg nil))
       (applyTo [this arglist]
                (let [arglist (vec arglist)]
                  (case (count arglist)
                    1 (.get this (first arglist)))))))))


(defmethod py-proto/pyobject-as-jvm :dict
  [pyobj & [opts]]
  (generic-python-as-map pyobj))


(defn generic-python-as-list
  [pyobj]
  (with-gil
    (let [dict-atts #{"__len__" "__getitem__" "__setitem__" "__iter__" "__contains__"
                      "__eq__" "__hash__" "clear" "insert" "pop" "append"
                      "__delitem__" "sort"}
          dict-att-map (->> (py-proto/dir pyobj)
                            (filter dict-atts)
                            (map (juxt identity (partial py-proto/get-attr pyobj)))
                            (into {}))
          py-call (fn [fn-name & args]
                    (with-gil (call-impl-fn fn-name dict-att-map args)))]
      (bridge-pyobject
       pyobj
       ObjectBuffer
       (lsize [reader]
              (long (py-call "__len__")))
       (readObject [reader idx]
             (py-call "__getitem__" idx))
       (sort [reader obj-com]
             (when-not (= nil obj-com)
               (throw (ex-info "Python lists do not support comparators" {})))
             (py-call "sort"))
       (writeObject [writer idx value]
              (py-call "__setitem__" idx value))
       (remove [writer ^int idx]
               (py-call "__delitem__" idx))
       (add [mutable idx value]
            (py-call "insert" idx value))
       (add [mutable value]
            (.add mutable (.size mutable) value))))))


(defmethod py-proto/pyobject-as-jvm :list
  [pyobj & [opts]]
  (generic-python-as-list pyobj))


(defmethod py-proto/pyobject-as-jvm :tuple
  [pyobj & [opts]]
  (generic-python-as-list pyobj))


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
                             (py-fn/cfn ~@args)))
                (fn [args]
                  `(~'invoke [~@args]
                    (apply py-fn/cfn ~@(butlast args) ~(last args))))))


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
                      (~'apply py-fn/cfn ~'this ~'arglist))))]
    `(bridge-pyobject ~pyobj ~interpreter
                      ~@fn-specs
                      ~@body)))


(defn python-obj-iterator
  [pyobj]
  (py-ffi/with-gil
    (py-ffi/with-decref [iter-attr (py-ffi/PyObject_GetAttrString pyobj "__iter__")]
      (when-not iter-attr (py-ffi/check-error-throw))
      (python->jvm-iterator iter-attr py-base/as-jvm))))


(defn generic-python-as-jvm
  "Given a generic pyobject, wrap it in a read-only map interface
  where the keys are the attributes."
  [pyobj]
  (with-gil
    (if (= :none-type (py-ffi/pyobject-type-kwd pyobj))
      nil
      (if (py-proto/callable? pyobj)
        (bridge-callable-pyobject
         pyobj
         Iterable
         (iterator [this] (python-obj-iterator pyobj))
         Fn)
        (bridge-pyobject
         pyobj
         Iterable
         (iterator [this] (python-obj-iterator pyobj)))))))


(defmethod py-proto/pyobject-as-jvm :default
  [pyobj & [opts]]
  (generic-python-as-jvm pyobj))
