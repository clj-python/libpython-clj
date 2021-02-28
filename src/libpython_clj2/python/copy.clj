(ns libpython-clj2.python.copy
  "Bindings to copy jvm <-> python.  All functions in this namespace expect the
  GIL to be captured."
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.gc :as pygc]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.protocols :as dt-proto]
            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.errors :as errors])
  (:import [tech.v3.datatype.ffi Pointer]
           [java.util Map RandomAccess Set]
           [clojure.lang Keyword Symbol IFn]))

(declare ->py-tuple)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; python -> jvm
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;protocol defaults and wrapper functions
(defn python->jvm-copy-hashmap
  [pyobj & [map-items]]
  (when-not (= 1 (py-ffi/PyMapping_Check pyobj))
    (errors/throwf "Object does not implement the mapping protocol: %s"
                   (py-proto/python-type pyobj)))
  (when-let [map-items (or map-items (py-ffi/PyMapping_Items pyobj))]
    (try
      (->> (py-base/->jvm map-items)
           (into {}))
      (finally
        (py-ffi/Py_DecRef map-items)))))


(defn python->jvm-copy-persistent-vector
  [pyobj]
  (when-not (= 1 (py-ffi/PySequence_Check pyobj))
    (errors/throwf "Object does not implement sequence protocol: %s"
                   (py-proto/python-type pyobj)))
  (->> (range (py-ffi/PySequence_Length pyobj))
       (mapv (fn [idx]
               (let [pyitem (py-ffi/PySequence_GetItem pyobj idx)]
                 (try
                   (py-base/->jvm pyitem)
                   (finally
                     (py-ffi/Py_DecRef pyitem))))))))


(defmethod py-proto/pyobject->jvm :str
  [pyobj & [options]]
  (py-ffi/pystr->str pyobj))


(defmethod py-proto/pyobject->jvm :int
  [pyobj & [options]]
  (py-ffi/PyLong_AsLongLong pyobj))


(defmethod py-proto/pyobject->jvm :float
  [pyobj & [options]]
  (py-ffi/PyFloat_AsDouble pyobj))


(defn pyobj-true?
  [pyobj]
  (= 1 (py-ffi/PyObject_IsTrue pyobj)))


(defmethod py-proto/pyobject->jvm :bool
  [pyobj & [options]]
  (pyobj-true? pyobj))


(defmethod py-proto/pyobject->jvm :tuple
  [pyobj & [options]]
  (let [n-elems (py-ffi/PyTuple_Size pyobj)]
    (mapv (fn [^long idx]
            (py-base/->jvm (py-ffi/PyTuple_GetItem pyobj idx)))
          (range n-elems))))


(defmethod py-proto/pyobject->jvm :dict
  [pyobj & [options]]
  (let [ppos (dt-ffi/make-ptr :size-t 0)
        pkey (dt-ffi/make-ptr :pointer 0)
        pvalue (dt-ffi/make-ptr :pointer 0)
        retval (java.util.ArrayList.)]
    ;;Dictionary iteration doesn't appear to be reentrant so we have
    ;;to do 2 passes.
    (loop [next-retval (py-ffi/PyDict_Next pyobj ppos pkey pvalue)]
      (if (not= 0 next-retval)
        (do
          (.add retval [(Pointer. (long (pkey 0)))
                        (Pointer. (long (pvalue 0)))])
          (recur (py-ffi/PyDict_Next pyobj ppos pkey pvalue)))
        (->> retval
             (map (fn [[k v]]
                    [(py-base/->jvm k) (py-base/->jvm v)]))
             (into {}))))))


(defn numpy-scalar->jvm
  [pyobj]
  (pygc/with-stack-context
    (-> (py-proto/get-attr pyobj "data")
        (py-proto/get-item (->py-tuple []))
        py-base/->jvm)))


(defmethod py-proto/pyobject->jvm :uint-8
  [pyobj & [opts]]
  (numpy-scalar->jvm pyobj))


(defmethod py-proto/pyobject->jvm :int-8
  [pyobj & [opts]]
  (numpy-scalar->jvm pyobj))


(defmethod py-proto/pyobject->jvm :uint-16
  [pyobj & [opts]]
  (numpy-scalar->jvm pyobj))


(defmethod py-proto/pyobject->jvm :int-16
  [pyobj & [opts]]
  (numpy-scalar->jvm pyobj))


(defmethod py-proto/pyobject->jvm :uint-32
  [pyobj & [opts]]
  (numpy-scalar->jvm pyobj))


(defmethod py-proto/pyobject->jvm :int-32
  [pyobj & [opts]]
  (numpy-scalar->jvm pyobj))


(defmethod py-proto/pyobject->jvm :uint-64
  [pyobj & [opts]]
  (numpy-scalar->jvm pyobj))


(defmethod py-proto/pyobject->jvm :int-64
  [pyobj & [opts]]
  (numpy-scalar->jvm pyobj))


(defmethod py-proto/pyobject->jvm :float-64
  [pyobj & [opts]]
  (numpy-scalar->jvm pyobj))


(defmethod py-proto/pyobject->jvm :float-32
  [pyobj & [opts]]
  (numpy-scalar->jvm pyobj))


(defmethod py-proto/pyobject->jvm :range
  [pyobj & [opts]]
  (pygc/with-stack-context
    (let [start (py-base/->jvm (py-proto/get-attr pyobj "start"))
          step (py-base/->jvm (py-proto/get-attr pyobj "step"))
          stop (py-base/->jvm (py-proto/get-attr pyobj "stop"))]
      (range start stop step))))


(defmethod py-proto/pyobject->jvm :default
  [pyobj & [options]]
  (cond
    (= :none-type (py-ffi/pyobject-type-kwd pyobj))
    nil
    ;;Things could implement mapping and sequence logically so mapping
    ;;takes precedence
    (= 1 (py-ffi/PyMapping_Check pyobj))
    (if-let [map-items (py-ffi/PyMapping_Items pyobj)]
      (try
        (python->jvm-copy-hashmap pyobj map-items)
        (finally
          (py-ffi/Py_DecRef map-items)))
      (do
        ;;Ignore error.  The mapping check isn't thorough enough to work for all
        ;;python objects.
        (py-ffi/PyErr_Clear)
        (python->jvm-copy-persistent-vector pyobj)))
    ;;Sequences become persistent vectors
    (= 1 (py-ffi/PySequence_Check pyobj))
    (python->jvm-copy-persistent-vector pyobj)
    :else
    {:type (py-ffi/pyobject-type-kwd pyobj)
     ;;Create a new GC root as the old reference is released.
     :value (let [new-obj (py-ffi/track-pyobject
                           (Pointer. (.address (dt-ffi/->pointer pyobj))))]
              (py-ffi/Py_IncRef new-obj)
              new-obj)}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; jvm -> python
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:dynamic *item-tuple-cutoff* 8)


(defn ->py-tuple
  [args]
  (-> (py-ffi/untracked-tuple args py-base/->python)
      (py-ffi/track-pyobject)))


(defn ->py-dict
  "Copy an object into a new python dictionary."
  [item]
  (py-ffi/check-gil)
  (-> (py-ffi/untracked-dict item py-base/->python)
      (py-ffi/track-pyobject)))


(defn ->py-string
  "Copy an object into a python string"
  [item]
  (-> (py-ffi/PyUnicode_FromString (str item))
      (py-ffi/track-pyobject)))


(defn ->py-list
  "Copy an object into a new python list."
  [item-seq]
  (py-ffi/check-gil)
  (let [item-seq (vec item-seq)
        retval (py-ffi/PyList_New (count item-seq))]
    (pygc/with-stack-context
      (dotimes [idx (count item-seq)]
        (let [si-retval
              ;;setitem does steal the reference
              (py-ffi/PyList_SetItem
               retval
               idx
               (py-ffi/untracked->python (item-seq idx) py-base/->python))]
          (when-not (== 0 (long si-retval))
            (py-ffi/check-error-throw)))))
    (py-ffi/track-pyobject retval)))


(defn ->py-set
  [item]
  (py-ffi/check-gil)
  (-> (py-ffi/PySet_New (->py-list item))
      (py-ffi/track-pyobject)))


(defn ->py-long
  [item]
  (py-ffi/track-pyobject (py-ffi/PyLong_FromLongLong (long item))))


(defn ->py-double
  [item]
  (py-ffi/track-pyobject (py-ffi/PyFloat_FromDouble (double item))))


(defn ->py-range
  [item]
  (let [dt-range (dt-proto/->range item {})
        start (long (dt-proto/range-start dt-range))
        inc (long (dt-proto/range-increment dt-range))
        n-elems (long (dtype/ecount dt-range))
        stop (+ start (* inc n-elems))
        ;;the tuple steals the references
        argtuple (py-ffi/untracked-tuple [start stop inc])
        retval (py-ffi/PyObject_CallObject (py-ffi/py-range-type) argtuple)]
    ;;we drop the tuple
    (py-ffi/Py_DecRef argtuple)
    ;;and wrap the retval
    (py-ffi/track-pyobject retval)))


(extend-protocol py-proto/PCopyToPython
  Boolean
  (->python [item opts]
    (if item (py-ffi/py-true) (py-ffi/py-false)))
  Long
  (->python [item opts] (->py-long item))
  Double
  (->python [item opts] (->py-double item))
  Number
  (->python [item opts]
    (if (integer? item)
      (->py-long item)
      (->py-double item)))
  String
  (->python [item ops] (->py-string item))
  Keyword
  (->python [item ops] (->py-string (name item)))
  Symbol
  (->python [item ops] (->py-string (name item)))
  Character
  (->python [item ops] (->py-string (str item)))
  Map
  (->python [item opts] (->py-dict item))
  RandomAccess
  (->python [item opts]
    (if (< (count item) (long *item-tuple-cutoff*))
      (->py-tuple item)
      (->py-list item)))
  Set
  (->python [item opts] (->py-set item))
  Pointer
  (->python [item opts] item)
  Object
  (->python [item opts]
    (cond
      (dt-proto/convertible-to-range? item)
      (->py-range item)
      (dtype/reader? item)
      (py-proto/->python (dtype/->reader item) opts)
      ;;There is one more case here for iterables (sequences)
      (instance? Iterable item)
      ;;Iterables we *have* to convert lazily; we cannot copy them.
      (py-proto/as-python item opts)
      (instance? IFn item)
      (py-proto/as-python item opts)
      :else
      (errors/throwf "Unable to convert object: %s" item))))
