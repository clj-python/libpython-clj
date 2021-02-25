(ns libpython-clj2.python.dechunk-map)


(defn dechunk-map
  "Map a function across a sequence without chunking."
  [f s]
  (lazy-seq
   (when-let [[x] (seq s)]
     (cons (f x) (dechunk-map f (rest s))))))
