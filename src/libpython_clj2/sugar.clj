(ns libpython-clj2.sugar
  (:require [libpython-clj2.python :as py]
            clojure.string))

(py/initialize!)


(let [{{:strs [pyxfn]} :globals}
      (py/run-simple-string "
import collections


class Deque:

    def __init__(self):
        self.q = collections.deque()

    def __iter__(self):
        while True:
            yield next(self)

    def __next__(self):
        return self.q.popleft()


    def append(self, x):
        self.q.appendleft(x)

    def pop(self):
        self.q.popleft()


def pyxfn(g, *args, **kwargs):
    q = Deque()
    gx = g(q, *args, **kwargs)
    return q, gx
")]
  (def ^:private -pyxfn pyxfn))

(let [builtins (py/import-module "builtins")
      pynext   (py/get-attr builtins "next")]
  (defn pyxfn
    [g & args]
    (fn [rf]
      (let [[q gx] (apply -pyxfn g args)]
        (fn
          ([] (rf))
          ([result] (rf result))
          ([result input]
           (py/$a q append input)
           (rf result (pynext gx))))))))


(defn ^:private handle-pydotdot!
  ([x form]
   (if (list? form)
     (let [form-data (vec form)
           [instance-member & args] form-data
           symbol-str (str instance-member)]
       (cond
         (= symbol-str "->")
         (list* '->  x args)

         (= symbol-str "->>")
         (list* '->> x args)

         (= symbol-str "as->")
         (let [[$ & args] args]
           (list* 'as-> x $ args))

         (= symbol-str "some->")
         (list* 'some-> x args)

         (= symbol-str "some->>")
         (list* 'some->> x args)

         (= symbol-str "cond->")
         (list* 'cond-> x args)

         (= symbol-str "cond->>")
         (list* 'cond->> x args)

         (get  #{"->py" "->python"} symbol-str)
         (list* #'py/->python x args)

         (= symbol-str "->jvm")
         (list* #'py/->jvm x args)

         (= symbol-str "doto")
         (list* 'doto x args)

         (clojure.string/starts-with? symbol-str "-")
         (list #'py/py.- x (symbol (subs symbol-str 1 (count symbol-str))))

         (clojure.string/starts-with? symbol-str "**")
         (list* #'py/py** x (symbol (subs symbol-str 2 (count symbol-str))) args)

         (clojure.string/starts-with? symbol-str "*")
         (list* #'py/py* x (symbol (subs symbol-str 1 (count symbol-str))) args)

         :else ;; assumed to be method invocation

         (list* (into (vector #'py/py. x instance-member) args))))
     (handle-pydotdot! x (list form))))
  ([x form & more]
   (apply handle-pydotdot! (handle-pydotdot! x form) more)))


(defmacro py!
  [x & args]
  (apply handle-pydotdot! x args))
