(ns libpython-clj2.python.gc
  "Binding of various sort of gc semantics optimized specifically for
  libpython-clj.  For general bindings, see tech.resource"
  (:import [java.util.concurrent ConcurrentHashMap ConcurrentLinkedDeque]
           [java.lang.ref ReferenceQueue]
           [tech.resource GCReference]))


(set! *warn-on-reflection* true)


(defonce ^:dynamic *stack-gc-context* nil)
(defn stack-context
  ^ConcurrentLinkedDeque []
  *stack-gc-context*)


(defonce reference-queue-var (ReferenceQueue.))
(defn reference-queue
  ^ReferenceQueue []
  reference-queue-var)


(defonce ptr-set-var (ConcurrentHashMap/newKeySet))
(defn ptr-set
  ^java.util.Set []
  ptr-set-var)


(defn track
  [item dispose-fn]
  (let [stack-context (stack-context)]
    (if (= stack-context :disabled)
      item
      (let [ptr-val (GCReference. item (reference-queue) (fn [ptr-val]
                                                           (.remove (ptr-set) ptr-val)
                                                           (dispose-fn)))]
        ;;We have to keep track of the pointer.  If we do not the pointer gets gc'd then
        ;;it will not be put on the reference queue when the object itself is gc'd.
        ;;Nice little gotcha there.
        (if stack-context
          (.add ^ConcurrentLinkedDeque stack-context ptr-val)
          ;;Ensure we don't lose track of the weak reference.  If it gets cleaned up
          ;;the gc system will fail.
          (.add (ptr-set) ptr-val))
        item))))


(defn clear-reference-queue
  []
  (when-let [next-ref (.poll (reference-queue))]
    (.run ^Runnable next-ref)
    (recur)))


(defn clear-stack-context
  []
  (when-let [next-ref (.pollLast (stack-context))]
    (.run ^Runnable next-ref)
    (recur)))


(defmacro with-stack-context
  [& body]
  `(with-bindings {#'*stack-gc-context* (ConcurrentLinkedDeque.)}
     (try
       ~@body
       (finally
         (clear-stack-context)))))


(defmacro with-disabled-gc
  [& body]
  `(with-bindings {#'*stack-gc-context* :disabled}
     ~@body))

(defn gc-context
  []
  *stack-gc-context*)


(defmacro with-gc-context
  [gc-ctx & body]
  `(with-bindings {#'*stack-gc-context* ~gc-ctx}
     ~@body))
