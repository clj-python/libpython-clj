(ns libpython-clj.stress-test
  "A set of tests meant to crash the system or just run the system out of
  memory if it isn't setup correctly."
  (:require [libpython-clj.python :as py]))


(defn get-data
  []
  (let [gd-fn
        (->  (py/run-simple-string "
def getdata():
    while True:
        yield {'a': 1, 'b': 2}
")
             :globals
             (get "getdata"))]
    (gd-fn)))

;;If you want to see how the sausage is made...
(alter-var-root #'libpython-clj.python.object/*object-reference-logging*
                (constantly false))

;;Ensure that failure to open resource context before tracking for stack
;;related things causes immediate failure.
(alter-var-root #'tech.resource.stack/*resource-context*
                (constantly nil))

(defn forever-test
  []
  (py/initialize! :no-io-redirect? false)
  (doseq [items (partition 999 (get-data))]
    ;;One way is to use the GC
    (time
     (do
       (last
        (eduction
         (map py/->jvm)
         (map (partial into {}))
         items))
       (System/gc)))
    ;;A faster way is to grab the gil and use the resource system
    ;;This also ensures that resources within that block do not escape
    (time
     (py/with-gil
       (py/stack-resource-context
        (last
         (eduction
          (map py/->jvm)
          (map (partial into {}))
          items)))))))


(defn str-marshal-test
  []
  (py/initialize!)
  (let [test-str (py/->python "a nice string to work with")]
    (time
     (py/with-gil
       (py/stack-resource-context
        (dotimes [iter 100000]
          (py/->jvm test-str)))))))


(defn dict-marshal-test
  []
  (py/initialize!)
  (let [test-item (py/->python {:a 1 :b 2})]
    (time
     (py/with-gil
       (py/stack-resource-context
        (dotimes [iter 10000]
          (py/->jvm test-item)))))))
