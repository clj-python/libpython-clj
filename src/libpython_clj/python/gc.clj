(ns libpython-clj.python.gc
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
  (let [ptr-val (GCReference. item (reference-queue) (fn [ptr-val]
                                                       (.remove (ptr-set) ptr-val)
                                                       (dispose-fn)))
        ^ConcurrentLinkedDeque stack-context (stack-context)]
    ;;We have to keep track of the pointer.  If we do not the pointer gets gc'd then
    ;;it will not be put on the reference queue when the object itself is gc'd.
    ;;Nice little gotcha there.
    (if stack-context
      (.add stack-context ptr-val)
      ;;Ensure we don't lose track of the weak reference.  If it gets cleaned up
      ;;the gc system will fail.
      (.add (ptr-set) ptr-val))
    item))


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
