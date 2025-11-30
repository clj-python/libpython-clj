(ns hooks.libpython-clj.require.require-python
  (:require [clj-kondo.hooks-api :as api]))

(def ^:private libpython-unary-flags
  #{:bind-ns :reload :no-arglists})

(def ^:private libpython-binary-flags
  #{:refer})

(defn- get-sexpr [node]
  (when node
    (try
      (api/sexpr node)
      (catch Exception _ nil))))

(defn- quoted-form?
  [sexpr]
  (and (seq? sexpr)
       (= 'quote (first sexpr))))

(defn- unquote-sexpr
  [sexpr]
  (if (quoted-form? sexpr)
    (second sexpr)
    sexpr))

(defn- extract-bind-ns-var
  [spec-data]
  (let [pairs (partition 2 (rest spec-data))
        bind-ns-val (some (fn [[k v]] (when (= :bind-ns k) v)) pairs)
        as-val (some (fn [[k v]] (when (= :as k) v)) pairs)]
    (when bind-ns-val
      (or as-val
          (when-let [first-sym (first spec-data)]
            (symbol (last (clojure.string/split (str first-sym) #"\."))))))))

(defn- extract-refer-symbols
  [spec-data]
  (let [pairs (partition 2 (rest spec-data))
        refer-val (some (fn [[k v]] (when (= :refer k) v)) pairs)]
    (when (and refer-val (vector? refer-val))
      refer-val)))

(defn- filter-spec-data
  [spec-data]
  (loop [result []
         remaining (rest spec-data)]
    (if (empty? remaining)
      (vec (cons (first spec-data) result))
      (let [item (first remaining)
            next-item (second remaining)]
        (cond
          (libpython-unary-flags item)
          (if (boolean? next-item)
            (recur result (drop 2 remaining))
            (recur result (rest remaining)))

          (libpython-binary-flags item)
          (recur result (drop 2 remaining))

          :else
          (recur (conj result item) (rest remaining)))))))

(defn- process-spec-data
  [sexpr]
  (let [unquoted (unquote-sexpr sexpr)]
    (cond
      (vector? unquoted)
      {:spec-data (filter-spec-data unquoted)
       :bind-ns-var (extract-bind-ns-var unquoted)
       :refer-symbols (extract-refer-symbols unquoted)}

      (symbol? unquoted)
      {:spec-data unquoted
       :bind-ns-var nil
       :refer-symbols nil}

      :else
      {:spec-data unquoted
       :bind-ns-var nil
       :refer-symbols nil})))

(defn- make-require-form
  [specs]
  (list* 'require
         (map (fn [spec] (list 'quote spec)) specs)))

(defn- make-def-form
  [var-name]
  (list 'def var-name nil))

(defn require-python
  [{:keys [node]}]
  (let [form (get-sexpr node)
        args (rest form)
        processed (map process-spec-data args)
        spec-data-list (map :spec-data processed)
        bind-ns-vars (filter some? (map :bind-ns-var processed))
        refer-symbols (mapcat :refer-symbols processed)
        require-form (make-require-form spec-data-list)
        def-forms (map make-def-form (concat bind-ns-vars refer-symbols))
        result-form (if (seq def-forms)
                      (list* 'do require-form def-forms)
                      require-form)
        result-node (api/coerce result-form)]
    {:node (with-meta result-node (meta node))}))
