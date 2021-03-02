(ns libpython-clj2.sugar
  (:require [libpython-clj2.python :as py]))

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
