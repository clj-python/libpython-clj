(ns libpython-clj.python.metadata
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


(def ^:private builtins (as-jvm (import-module "builtins") {}))
(def ^:private inspect (as-jvm (import-module "inspect") {}))
(def ^:private argspec (get-attr inspect "getfullargspec"))
(def ^:private py-source (get-attr inspect "getsource"))
(def ^:private types (import-module "types"))
(def ^:private fn-type
  (call-attr builtins "tuple"
             [(get-attr types "FunctionType")
              (get-attr types "BuiltinFunctionType")]))

(def ^:private method-type
  (call-attr builtins "tuple"
             [(get-attr types "MethodType")
              (get-attr types "BuiltinMethodType")]))

(def ^:private isinstance? (get-attr builtins "isinstance"))
(def ^:private fn? #(isinstance? % fn-type))
(def ^:private method? #(isinstance? % method-type))
(def ^:private doc #(try
                      (get-attr % "__doc__")
                      (catch Exception e
                        "")))
(def ^{:private false :public true}
  get-pydoc doc)
(def ^:private vars (get-attr builtins "vars"))
(def ^:private pyclass? (get-attr inspect "isclass"))
(def ^:private pymodule? (get-attr inspect "ismodule"))
(def ^:private importlib_util (import-module "importlib.util"))
(defn ^:private findspec [x]
  (let [-findspec
        (-> importlib_util (get-attr "find_spec"))]
    (-findspec x)))

(defn ^:private py-fn-argspec [f]
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

(defn ^:private py-class-argspec [class]
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
         pos-args        (transduce
                          (comp
                           (take n-pos-args)
                           (map symbol))
                          (completing conj)
                          []
                          args)
         kw-default-args (transduce
                          (comp
                           (drop n-pos-args)
                           (map symbol))
                          (completing conj)
                          []
                          args)
         or-map          (transduce
                          (comp
                           (partition-all 2)
                           (map vec)
                           (map (fn [[k v]] [(symbol k) v])))
                          (completing (partial apply assoc))
                          {}
                          (concat
                           (interleave kw-default-args defaults)
                           (flatten (seq kwonlydefaults))))

         as-varkw    (when (not (nil? varkw))
                       {:as (symbol varkw)})
         default-map (transduce
                      (comp
                       (partition-all 2)
                       (map vec)
                       (map (fn [[k v]] [(symbol k) (keyword k)])))
                      (completing (partial apply assoc))
                      {}
                      (concat
                       (interleave kw-default-args defaults)
                       (flatten (seq kwonlydefaults))))

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


(extend-type PPyObject
  clj-proto/Datafiable
  (datafy [item]
    (with-meta
      (with-gil
        (->> (vars item)
             (py-proto/as-map)
             (map (fn [[att-name att-val]]
                    (when att-val
                      (try
                        (let [att-type (py-proto/python-type att-val)]
                          [att-name
                           (merge {:type att-type
                                   :doc (doc att-val)
                                   :str (.toString att-val)
                                   :flags (->> {:pyclass? pyclass?
                                                :callable? callable?
                                                :fn? fn?
                                                :method? method?
                                                :pymodule? pymodule?}
                                               (map (fn [[kwd f]]
                                                      (when (f att-val)
                                                        kwd)))
                                               (remove nil?)
                                               set)}

                                  (when (callable? att-val)
                                    (py-fn-metadata att-name att-val {}))
                                  (when (has-attr? att-val "__module__")
                                    {:module (get-attr att-val "__module__")})
                                  (when (has-attr? att-val "__name__")
                                    {:name (get-attr att-val "__name__")}))])
                        (catch Throwable e
                          (log/warnf "Metadata generation failed for %s:%s"
                                     (.toString item)
                                     att-name)
                          nil)))))
             (remove nil?)
             (into {})))
      {`clj-proto/nav
       (fn nav-pyval
         [coll f val]
         (cond
           (= :module (:type val))
           (as-jvm (import-module (:name val)) {})
           (= :type (:type val))
           (let [mod (as-jvm (import-module (:module val)) {})
                 cls-obj (get-attr mod (:name val))]
             cls-obj)
           :else
           val))})))
