(ns libpython-clj.metadata
  (:refer-clojure :exclude [fn? doc])
  (:require [libpython-clj.python
             :refer [import-module as-jvm get-attr call-attr callable? has-attr?
                     ->jvm with-gil]
             :as py]
            [libpython-clj.python.protocols :as py-proto]
            [clojure.core.protocols :as clj-proto]
            [clojure.tools.logging :as log])
  (:import [libpython_clj.python.protocols PPyObject]))


(py/initialize!)


(def builtins (as-jvm (import-module "builtins") {}))
(def inspect (as-jvm (import-module "inspect") {}))
(def argspec (get-attr inspect "getfullargspec"))
(def py-source (get-attr inspect "getsource"))
(def types (import-module "types"))
(def fn-type
  (call-attr builtins "tuple"
             [(get-attr types "FunctionType")
              (get-attr types "BuiltinFunctionType")]))

(def method-type
  (call-attr builtins "tuple"
             [(get-attr types "MethodType")
              (get-attr types "BuiltinMethodType")]))

(def isinstance? (get-attr builtins "isinstance"))
(def fn? #(isinstance? % fn-type))
(def method? #(isinstance? % method-type))
(def doc #(try
             (get-attr % "__doc__")
             (catch Exception e
               "")))
(def get-pydoc doc)
(def vars (get-attr builtins "vars"))
(def pyclass? (get-attr inspect "isclass"))
(def pymodule? (get-attr inspect "ismodule"))
(def importlib (py/import-module "importlib"))
(def importlib_util (import-module "importlib.util"))
(def reload-module (py/get-attr importlib "reload"))
(defn findspec [x]
  (let [-findspec
        (-> importlib_util (get-attr "find_spec"))]
    (-findspec x)))

(defn py-fn-argspec [f]
  (if-let [spec (try (when-not (pyclass? f)
                       (argspec f))
                     (catch Throwable e nil))]
    {:args           (->jvm (get-attr spec "args") {})
     :varargs        (->jvm (get-attr spec "varargs") {})
     :varkw          (->jvm (get-attr spec "varkw") {})
     :defaults       (->jvm (get-attr spec "defaults") {})
     :kwonlyargs     (->jvm (get-attr spec "kwonlyargs") {})
     :kwonlydefaults (->jvm (get-attr spec "kwonlydefaults") {})
     :annotations    (->jvm (get-attr spec "annotations") {})}
    (py-fn-argspec (get-attr f "__init__"))))

(defn py-class-argspec [class]
  (let [constructor (get-attr class "__init__")]
    (py-fn-argspec constructor)))


(defn pyargspec [x]
  ;; TODO: certain builtin functions have
  ;;   ..: signatures that are found in the first line
  ;;   ..: of their docstring, aka, print.
  ;;   ..: These seem to be uniform enough that
  ;;   ..: most IDEs have a way of creating stubs
  ;;   ..: for the signature.  If there is a uniform way
  ;;   ..: to do this that doesn't simply involve an
  ;;   ..: army of devs doing transcription I'd like to
  ;;   ..: pull that in here
  (cond
    (fn? x) (py-fn-argspec x)
    (method? x) (py-fn-argspec x)
    ;; (builtin-function? x) (py-builtin-fn-argspec x)
    ;; (builtin-method? x) (py-builtin-method-argspec x)
    (string? x) ""
    (number? x) ""
    :else (py-class-argspec x)))


(defn pyarglists
  ([argspec] (pyarglists argspec
                         (if-let [defaults
                                  (not-empty (:defaults argspec))]
                           defaults
                           [])))
  ([argspec defaults] (pyarglists argspec defaults []))
  ([{:as            argspec
     args           :args
     varkw          :varkw
     varargs        :varargs
     kwonlydefaults :kwonlydefaults
     kwonlyargs     :kwonlyargs}
    defaults res]
   (let [n-args          (count args)
         n-defaults      (count defaults)
         n-pos-args      (- n-args n-defaults)
         pos-args        (->> args
                              (take n-pos-args)
                              (map symbol)
                              (into []))
         kw-default-args (->> args
                              (drop n-pos-args)
                              (map symbol)
                              (into []))
         or-map          (->> (concat
                               (interleave kw-default-args defaults)
                               (flatten (seq kwonlydefaults)))
                              (partition-all 2)
                              (map vec)
                              (map (fn [[k v]] [(symbol k) v]))
                              (into {}))
         as-varkw    (when (not (nil? varkw))
                       {:as (symbol varkw)})
         default-map (->> (concat
                           (interleave kw-default-args defaults)
                           (flatten (seq kwonlydefaults)))
                          (partition-all 2)
                          (map vec)
                          (map (fn [[k v]] [(symbol k) (keyword k)]))
                          (into {}))

         kwargs-map (merge default-map
                           (when (not-empty or-map)
                             {:or or-map})
                           (when (not-empty as-varkw)
                             as-varkw))
         opt-args
         (cond
           (and (empty? kwargs-map)
                (nil? varargs)) '()
           (empty? kwargs-map)  (list '& [(symbol varargs)])
           (nil? varargs)       (list '& [kwargs-map])
           :else                (list '& [(symbol varargs)
                                          kwargs-map]))

         arglist  ((comp vec concat) (list* pos-args) opt-args)]
     (let [arglists  (conj res arglist)
           defaults' (if (not-empty defaults) (pop defaults) [])
           argspec'  (update argspec :args
                             (fn [args]
                               (if (not-empty args)
                                 (pop args)
                                 args)))]

       (if (and (empty? defaults) (empty? defaults'))
         arglists
         (recur argspec' defaults' arglists))))))


(defn py-class-argspec [class]
  (let [constructor (py/get-attr class "__init__")]
    (py-fn-argspec constructor)))


(defn py-fn-metadata [fn-name x {:keys [no-arglists?]}]
  (let [fn-argspec (pyargspec x)
        fn-docstr  (get-pydoc x)]
    (merge
     fn-argspec
     {:doc  fn-docstr
      :name fn-name}
     (when (and (callable? x)
                (not no-arglists?))
       (try
         {:arglists (pyarglists fn-argspec)}
         (catch Throwable e
           nil))))))

(defn pyobj-flags
  [item]
  (->> {:pyclass? pyclass?
        :callable? callable?
        :fn? fn?
        :method? method?
        :pymodule? pymodule?}
       (map (fn [[kwd f]]
              (when (f item)
                kwd)))
       (remove nil?)
       set))


(defn base-pyobj-map
  [item]
  (merge {:type (py/python-type item)
          :doc (doc item)
          :str (.toString item)
          :flags (pyobj-flags item)}
         (when (has-attr? item "__module__")
           {:module (get-attr item "__module__")})
         (when (has-attr? item "__name__")
           {:name (get-attr item "__name__")})))


(defn scalar?
  [att-val]
  (or (string? att-val)
      (number? att-val)))

(defn datafy-module [item]
  (with-gil
    (->> (if (or (pyclass? item)
                 (pymodule? item))
           (-> (vars item)
               (py-proto/as-map))
           (->> (py/dir item)
                (map (juxt identity #(get-attr item %)))))
         (map (fn [[att-name att-val]]
                (when att-val
                  (try
                    [att-name
                     (merge (base-pyobj-map att-val)
                            (when (callable? att-val)
                              (py-fn-metadata att-name att-val {}))
                            (when (scalar? att-val)
                              {:value att-val}))]
                    (catch Throwable e
                      (log/warnf "Metadata generation failed for %s:%s"
                                 (.toString item)
                                 att-name)
                      nil)))))
         (remove nil?)
         (into (base-pyobj-map item)))))

(defn nav-module [coll f val]
  (with-gil
    (if (map? val)
      (cond
        (= :module (:type val))
        (as-jvm (import-module (:name val)) {})
        (= :type (:type val))
        (let [mod (as-jvm (import-module (:module val)) {})
              cls-obj (get-attr mod (:name val))]
          cls-obj)
        :else
        val)
      val)))


(defn module-path-string
  "Given a.b, return a
   Given a.b.c, return a.b
   Given a.b.c.d, return a.b.c  etc."
  [x]
  (clojure.string/join
   "."
   (pop (clojure.string/split (str x) #"[.]"))))


(defn module-path-last-string
  "Given a.b.c.d, return d"
  [x]
  (last (clojure.string/split (str x) #"[.]")))


(defn path->py-obj
  [item-path & {:keys [reload?]}]
  (when (seq item-path)
    (if-let [module-retval (try
                             (import-module item-path)
                             (catch Throwable e nil))]
      (if reload?
        (reload-module module-retval)
        module-retval)
      (let [butlast (module-path-string item-path)]
        (if-let [parent-mod (path->py-obj butlast :reload? reload?)]
          (get-attr parent-mod (module-path-last-string item-path))
          (throw (Exception. (format "Failed to find module or class %s"
                                     item-path))))))))


(defn metadata-map->py-obj
  [metadata-map]
  (case (:type metadata-map)
    :module (import-module (:name metadata-map))
    :type (-> (import-module (:module metadata-map))
              (get-attr (:name metadata-map)))))


(defn get-or-create-namespace!
  [ns-symbol ns-doc]
  (if-let [ns-obj (find-ns ns-symbol)]
    ns-obj
    (create-ns (with-meta ns-symbol
                 (merge (meta ns-symbol)
                        {:doc ns-doc})))))


(defn apply-static-metadata-to-namespace!
  "Given a metadata map, find the item associated with the map and for each
  string keyword apply it to the namespace.  Namespace is created if it does not
  already exist.  Returns the namespace symbol."
  [ns-symbol metadata-map & {:keys [no-arglists?]}]
  (let [target-item (metadata-map->py-obj metadata-map)]
    (get-or-create-namespace! ns-symbol (:doc metadata-map))
    (doseq [[k v] metadata-map]
      (when (and (string? k)
                 (map? v)
                 (has-attr? target-item k))
        (let [att-val (get-attr target-item k)]
          (intern ns-symbol
                  (with-meta (symbol k)
                    (if no-arglists?
                      (dissoc v :arglists)
                      v))
                  att-val))))
    ns-symbol))


(defn apply-instance-metadata-to-namespace!
  "In this case we have at some point in the past generated metadata from an instance
  and we want to create an namespace to get intellisense on objects of that type.
  The use case for this is you get a generic 'thing' back and you export its metadata
  to a resource edn file.  You can then always create a namespace from this metadata
  and use that namespace to explore/use the instance and this will work regardless
  of if a factory returns a derived object.
  Returns the namespace symbol."
  [ns-symbol metadata-map]
  (get-or-create-namespace! ns-symbol (:doc metadata-map))
  (doseq [[k v] metadata-map]
    (when (map? v)
      (cond
        (contains? (:flags v) :callable?)
        (intern ns-symbol (with-meta (symbol k) v)
                (fn [inst & args]
                  (apply (get-attr inst k) args)))
        (contains? v :value)
        (intern ns-symbol (with-meta (symbol k) v) (:value v)))))
  ns-symbol)
