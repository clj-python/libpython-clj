(ns libpython-clj.jna.base
  (:require [tech.v3.jna :as jna]
            [tech.v3.jna.base :as jna-base])
  (:import [com.sun.jna Pointer NativeLibrary]
           [libpython_clj.jna PyObject]
           [java.util.concurrent.atomic AtomicLong]))

(set! *warn-on-reflection* true)

(def ^:dynamic *python-library* "python3.6m")

(def ^:dynamic *python-library-names* ["python3.7m" "python3.6m"])


(defn library-names
  []
  *python-library-names*)


(defprotocol PToPyObjectPtr
  (convertible-to-pyobject-ptr? [item])
  (->py-object-ptr [item]))


(extend-type PyObject
  PToPyObjectPtr
  (convertible-to-pyobject-ptr? [item] true)
  (->py-object-ptr [item] (.getPointer item)))


(extend-type Pointer
  PToPyObjectPtr
  (convertible-to-pyobject-ptr? [item] true)
  (->py-object-ptr [item] item))

(extend-type Object
  PToPyObjectPtr
  (convertible-to-pyobject-ptr? [item] (jna/is-jna-ptr-convertible? item))
  (->py-object-ptr [item] (jna/->ptr-backing-store item)))


(defn as-pyobj
  [item]
  (when (and item (convertible-to-pyobject-ptr? item))
    (->py-object-ptr item)))


(defn ensure-pyobj
  [item]
  (if-let [retval (as-pyobj item)]
    retval
    (throw (ex-info "Failed to get a pyobject pointer from object." {}))))


(defn ensure-pydict
  "The return value of this has to be a python dictionary object."
  [item]
  (ensure-pyobj item))


(defn ensure-pytuple
  "The return value of this has to be a python tuple object."
  [item]
  (ensure-pyobj item))


(definline current-thread-id
  ^long []
  (-> (Thread/currentThread)
      (.getId)))

(defonce gil-thread-id (AtomicLong. Long/MAX_VALUE))


(defn set-gil-thread-id!
  ^long [^long expected ^long new-value]
  (when-not (.compareAndSet ^AtomicLong gil-thread-id expected new-value)
    (throw (Exception. "Failed to set gil thread id")))
  new-value)


(defmacro def-no-gil-pylib-fn
  "Define a pylib function where the gil doesn't need to be captured to call."
  [fn-name docstring & args]
  `(jna/def-jna-fn *python-library* ~fn-name ~docstring ~@args))


(defmacro def-pylib-fn
  [fn-name docstring rettype & argpairs]
  `(defn ~fn-name
     ~docstring
     ~(mapv first argpairs)
     (when-not (== (current-thread-id) (.get ^AtomicLong gil-thread-id))
       (throw (Exception. "Failure to capture gil when calling into libpython")))
     (let [~'tvm-fn (jna/find-function ~(str fn-name) *python-library*)
           ~'fn-args (object-array
                      ~(mapv (fn [[arg-symbol arg-coersion]]
                               (when (= arg-symbol arg-coersion)
                                 (throw (ex-info (format "Argument symbol (%s) cannot match coersion (%s)"
                                                         arg-symbol arg-coersion)
                                                 {})))
                               `(~arg-coersion ~arg-symbol))
                             argpairs))]
       ~(if rettype
          `(.invoke (jna-base/to-typed-fn ~'tvm-fn) ~rettype ~'fn-args)
          `(.invoke (jna-base/to-typed-fn ~'tvm-fn) ~'fn-args)))))


(defonce size-t-type (type (jna/size-t 0)))


(defn find-pylib-symbol
  ^Pointer [sym-name]
  (.getGlobalVariableAddress ^NativeLibrary (jna-base/load-library *python-library*)
                             sym-name))
