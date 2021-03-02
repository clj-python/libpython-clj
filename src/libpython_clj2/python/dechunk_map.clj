(ns libpython-clj2.python.dechunk-map
  "Utility namespace with a function that works like a single-sequence map but
  stops chunking.")


(defn dechunk-map
  "Map a function across a sequence without chunking."
  [f s]
  (lazy-seq
   (when-let [[x] (seq s)]
     (cons (f x) (dechunk-map f (rest s))))))
