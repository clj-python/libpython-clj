(ns libpython-clj.require
  (:refer-clojure :exclude [fn? doc])
  (:require [libpython-clj.python :as py]
            [clojure.tools.logging :as log]))

(py/initialize!)

(def ^:private builtins (py/import-module "builtins"))

(def ^:private inspect (py/import-module "inspect"))

(def ^:private argspec (py/get-attr inspect "getfullargspec"))

(def ^:private py-source (py/get-attr inspect "getsource"))

(def ^:private types (py/import-module "types"))

(def ^:private fn-type
  (py/call-attr builtins "tuple"
                [(py/get-attr types "FunctionType")
                 (py/get-attr types "BuiltinFunctionType")]))

(def ^:private method-type
  (py/call-attr builtins "tuple"
                [(py/get-attr types "MethodType")
                 (py/get-attr types "BuiltinMethodType")]))

(def ^:private isinstance? (py/get-attr builtins "isinstance"))

(def ^:private fn? #(isinstance? % fn-type))

(def ^:private method? #(isinstance? % method-type))

(def ^:private doc #(try
                      (py/get-attr % "__doc__")
                      (catch Exception e
                        "")))

(def ^:private importlib (py/import-module "importlib"))

(def ^:private reload-module (py/get-attr importlib "reload"))

(def ^:priviate import-module (py/get-attr importlib "import_module"))

(def ^{:private false :public true}
  get-pydoc doc)

(def ^:private vars (py/get-attr builtins "vars"))

(defn ^:private py-fn-argspec [f]
  (if-let [spec (try (argspec f) (catch Throwable e nil))]
    {:args           (py/->jvm (py/get-attr spec "args"))
     :varargs        (py/->jvm (py/get-attr spec "varargs"))
     :varkw          (py/->jvm (py/get-attr spec "varkw"))
     :defaults       (py/->jvm (py/get-attr spec "defaults"))
     :kwonlyargs     (py/->jvm  (py/get-attr spec "kwonlyargs"))
     :kwonlydefaults (py/->jvm  (py/get-attr spec "kwonlydefaults"))
     :annotations    (py/->jvm  (py/get-attr spec "annotations"))}
    (py-fn-argspec (py/get-attr f "__init__"))))

(defn ^:private py-class-argspec [class]
  (let [constructor (py/get-attr class "__init__")]
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
     (when (and (py/callable? x)
                (not no-arglists?))
       (try
         {:arglists (pyarglists fn-argspec)}
         (catch Throwable e
           nil))))))


(defn ^:private load-py-fn [f fn-name fn-module-name-or-ns
                            options]
  (let [fn-ns      (symbol (str fn-module-name-or-ns))
        fn-sym     (symbol fn-name)]
    (intern fn-ns (with-meta fn-sym (py-fn-metadata fn-name f
                                                    options)) f)))


(defn ^:private load-python-lib [req]
  (let [supported-flags     #{:reload :no-arglists}
        [module-name & etc] req
        flags               (into #{}
                                  (filter supported-flags)
                                  etc)
        etc                 (into {}
                                  (comp
                                   (remove supported-flags)
                                   (partition-all 2)
                                   (map vec))
                                  etc)
        reload?             (:reload flags)
        no-arglists?        (:no-arglists flags)
        module-name-or-ns   (:as etc module-name)
        exclude             (into #{} (:exclude etc))
        refer          (cond
                         (= :all (:refer etc)) #{:all}
                         (= :* (:refer etc))   #{:*}
                         :else                 (into
                                                #{}
                                                (:refer etc)))
        current-ns     *ns*
        current-ns-sym (symbol (str current-ns))
        python-namespace (find-ns module-name-or-ns)
        this-module (import-module (str module-name))]

    (cond
      reload?
      (do
        (remove-ns module-name)
        (reload-module this-module))
      (not python-namespace)
      (create-ns module-name-or-ns))

    ;; bind the python module to its symbolic name
    ;; in the current namespace


    ;; create namespace for module and bind python
    ;; values to namespace symbols
    (when (or reload?
              (not python-namespace))
      ;;Mutably define the root namespace.
      (doseq [[att-name v] (vars this-module)]
        (try
          (when v
            (if (py/callable? v)
              (load-py-fn v (symbol att-name) module-name-or-ns
                          {:no-arglists?
                           no-arglists?})
              (intern module-name-or-ns (symbol att-name) v)))
          (catch Throwable e
            (log/warnf e "Failed to require symbol %s" att-name)))))


    (let [python-namespace (find-ns module-name-or-ns)
          ;;ns-publics is a map of symbol to var.  Var's have metadata on them.
          public-data (->> (ns-publics python-namespace)
                           (remove #(exclude (first %)))
                           (into {}))]

      ;;Always make the loaded namespace available to the current namespace.
      (intern current-ns-sym
              (with-meta module-name-or-ns
                {:doc (doc this-module)})
              this-module)
      (let [refer-symbols
            (cond
              ;; include everything into the current namespace,
              ;; ignore __all__ directive
              (or (refer :all)
                  (and (not (py/has-attr? this-module "__all__"))
                       (refer :*)))
              (keys public-data)
              ;; only include that specfied by __all__ attribute
              ;; of python module if specified, else same as :all
              (refer :*)
              (->> (py/get-attr this-module "__all__")
                   (map (fn [item-name]
                          (let [item-sym (symbol item-name)]
                            (when (contains? public-data item-sym)
                              item-sym))))
                   (remove nil?))

              ;; [.. :refer [..]] behavior
              :else
              (do
                (when-let [missing (->> refer
                                        (remove (partial contains? public-data))
                                        seq)]
                  (throw (Exception.
                          (format "'refer' symbols not found: %s"
                                  (vec missing)))))
                refer))]
        (doseq [[s v] (select-keys public-data refer-symbols)]
          (intern current-ns-sym
                  (with-meta s (meta v))
                  (deref v)))))))

(defn require-python
  "## Basic usage ##

   (require-python 'math)
   (math/sin 1.0) ;;=> 0.8414709848078965

   (require-python '[math :as maaaath])

   (maaaath/sin 1.0) ;;=> 0.8414709848078965

   (require-python '(math csv))
   (require-python '([math :as pymath] csv))
   (require-python '([math :as pymath] [csv :as py-csv])
   (require-python 'concurrent.futures)
   (require-python '[concurrent.futures :as fs])

   (require-python '[requests :refer [get post]])

   (requests/get \"https//www.google.com\") ;;=>  <Response [200]>
   (get \"https//www.google.com\") ;;=>  <Response [200]>

   In some cases we may generate invalid arglists metadata for the clojure compiler.
   In those cases we have a flag, :no-arglists that will disable adding arglists to
   the generated metadata for the vars.  Use the reload flag below if you need to
   force reload a namespace where invalid arglists have been generated.

   (require-python '[numpy :refer [linspace] :no-arglists :as np])

   ## Use with custom modules ##

   For use with a custom namespace foo.py while developing, you can
   use:

   (require-python '[foo :reload])

   NOTE: unless you specify the :reload flag,
     ..: the module will NOT reload.  If the :reload flag is set,
     ..: the behavior mimics importlib.reload

   ## Setting up classpath for custom modules ##

   Note: you may need to setup your PYTHONPATH correctly.
   One technique to do this is, if your foo.py lives at
   /path/to/foodir/foo.py:

   (require-python 'sys)
   (py/call-attr (py/get-attr sys \"path\")
                 \"append\"
                 \"/path/to/foodir\")

   Another option is

   (require-python 'os)
   (os/chdir \"/path/to/foodir\")


   ## For library developers ##

   If you want to intern all symbols to your current namespace,
   you can do the following --

   (require-python '[math :refer :all])

   However, if you only want to use
   those things designated by the module under the __all__ attribute,
   you can do

   (require-python '[operators :refer :*])"
  [reqs]

  (cond
    (list? reqs)
    (doseq [req (vec reqs)] (require-python req))
    (symbol? reqs)
    (load-python-lib (vector reqs))
    (vector? reqs)
    (load-python-lib reqs)))
