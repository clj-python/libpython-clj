(ns libpython-clj2.python.jvm-handle
  (:require [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.gc :as pygc])
  (:import [java.util.concurrent ConcurrentHashMap]
           [java.util UUID]))


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
  (long (py-proto/get-attr self "jvm_handle")))


(defn py-self->jvm-obj
  ^Object [self]
  (-> (py-self->jvm-handle self)
      get-jvm-object))


(defn py-self-set-jvm-handle!
  [self hdl]
  (py-proto/set-attr! self "jvm_handle" (long hdl))
  nil)


(defmacro py-global-delay
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
