(ns libpython-clj.require
  (:refer-clojure :exclude [fn? doc])
  (:require [libpython-clj.python :as py]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]))

(py/initialize!)

;; for hot reloading multimethod in development
(ns-unmap 'libpython-clj.require 'intern-ns-class)

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

(def ^:private importlib_util (py/import-module "importlib.util"))

(def ^:private reload-module (py/get-attr importlib "reload"))

(def ^:priviate import-module (py/get-attr importlib "import_module"))

(def ^{:private false :public true}
  get-pydoc doc)

(def ^:private vars (py/get-attr builtins "vars"))

(def ^:private pyclass? (py/get-attr inspect "isclass"))

(def ^:private pymodule? (py/get-attr inspect "ismodule"))

(defn ^:private findspec [x]
  (let [-findspec
        (-> importlib_util (py/get-attr "find_spec"))]
    (-findspec x)))

(def ^:private hasattr (py/get-attr builtins "hasattr"))

(defn ^:private module-string? [x]
  (try
    (findspec x)
    (catch Throwable e
      false)))

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

(defn ^:private possible-class?
  "The presumption here is that, given a symbol like 'builtins.list,
  if 'builtins.list does not have a __path__ attribute but 'builtins does,
  it's possible that 'list is a class of 'builtins.

  This is a slightly cheaper test than a try/except wrapper around
  a module."
  [pos-class]
  (let [module? (module-path-string pos-class)]
    (and (not (module-string? pos-class))
         (boolean (module-string? module?)))))

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
    (intern fn-ns
            (with-meta
              fn-sym
              (py-fn-metadata fn-name f
                              options)) f)))

(defn- parse-flags
  "FSM style parser for flags.  Designed to support both
  unary style flags aka '[foo :reload] and
  boolean flags '[foo :reload true] to support Clojure
  style 'require syntax.  Possibly overengineered."
  [supported-flags reqs]
  ;; Ensure we error out when flags passed in are mistyped.
  ;; First attempt is to filter keywords and make sure any keywords are
  ;; in supported-flags
  (let [total-flags (set (concat supported-flags [:as :refer :exclude
                                                  :* :all]))]
    (when-let [missing-flags (->> reqs
                                  (filter #(and (not (total-flags %))
                                                (keyword? %)))
                                  seq)]
      (throw (Exception. (format "Unsupported flags: %s"
                                 (set missing-flags))))))
  (letfn [(supported-flag-item
            ;; scanned a supported tag token
            [supported-flags flag results item items]
            (cond
              ;; add flag, continue scanning
              (true? item) (next-flag-item
                            supported-flags
                            (conj results flag)
                            (first items)
                            (rest items))

              ;; don't add flag, continue scanning
              (false? item) (next-flag-item
                             supported-flags
                             results
                             (first items)
                             (rest items))
              :else
              ;; unary flag -- add flag but scan current item/s
              (next-flag-item
               supported-flags
               (conj  results flag)
               item
               items)))

          ;; scan flags
          (next-flag-item [supported-flags results item items]
            (cond
              ;; supported flag scanned, begin FSM parse
              (get supported-flags item)
              (let [flag            (get supported-flags item)
                    remaining-flags (clojure.set/difference
                                     supported-flags #{flag})]
                (supported-flag-item
                 remaining-flags
                 flag
                 results
                 (first items)
                 (rest items)))

              ;; FSM complete
              (nil? item) (into #{} results)

              ;; no flag scanned, continue scanning
              :else (recur
                     supported-flags
                     results
                     (first items)
                     (rest items))))

          ;; entrypoint
          (get-flags [supported-flags reqs]
            (next-flag-item supported-flags
                            []
                            (first reqs)
                            (rest reqs)))]
    (trampoline get-flags supported-flags reqs)))

(defn- extract-refer-symbols
  [{:keys [refer this-module]} public-data]
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
      refer)))

(defn import-module-or-cls! [x]
  (if (possible-class? x)
    (let [module? (module-path-string x)
          class?  (module-path-last-string x)]
      (try
        (let [m   (import-module module?)
              cls (py/get-attr m class?)]
          (if (nil? cls)
            (throw (Exception. "Unable to load " x)))
          [m cls])
        (catch Exception e
          (log/error (str "Unable to load " x))
          (throw e))))
    [(import-module (str x)) nil]))

(defn- python-lib-configuration
  "Build a configuration map of a python library.  Current ns is option and used
  during testing but unnecessary during normal running events."
  [req & [current-ns]]
  (let [supported-flags     #{:reload :no-arglists :alpha-load-ns-classes}
        [module-name & etc] req
        flags               (parse-flags supported-flags etc)
        etc                 (into {}
                                  (comp
                                   (remove supported-flags)
                                   (remove boolean?)
                                   (partition-all 2)
                                   (map vec))
                                  etc)
        reload?             (:reload flags)
        no-arglists?        (:no-arglists flags)
        load-ns-classes?    (:alpha-load-ns-classes flags)
        module-name-or-ns   (:as etc module-name)
        exclude             (into #{} (:exclude etc))
        refer               (cond
                              (= :all (:refer etc)) #{:all}
                              (= :* (:refer etc))   #{:*}
                              :else                 (into
                                                     #{}
                                                     (:refer etc)))
        current-ns          (or current-ns *ns*)
        current-ns-sym      (symbol (str current-ns))
        python-namespace    (find-ns module-name-or-ns)
        [this-module cls]   (import-module-or-cls! module-name)]

    {:supported-flags   supported-flags
     :etc               etc
     :reload?           reload?
     :no-arglists?      no-arglists?
     :load-ns-classes?  true

     ;; if something like 'builtins.list was requested,
     ;; the module name is 'builtins
     :module-name       (if (pyclass? cls)
                          (module-path-string module-name)
                          module-name)

     ;; if something like 'builtins.list was requested,
     ;; the module-name is 'builtins
     ;; or in the situation of
     ;; '[builtins.list :as python.pylist]
     ;; 'python would be bound to :module-name-or-ns
     :module-name-or-ns (if (and (pyclass? cls)
                                 (or (symbol? module-name-or-ns)
                                     (string? module-name-or-ns)))
                          (module-path-string module-name-or-ns)
                          module-name-or-ns)

     ;; presumes possibilty of alias in situations like
     ;; '[builtins.list :as python.pylist]
     ;; 'pylist would be bound to :class-name-or-ns in this case

     :class-name-or-ns (if (and (pyclass? cls)
                                (or (symbol? module-name-or-ns)
                                    (string? module-name-or-ns)))
                         (module-path-last-string module-name-or-ns)
                         nil)

     :exclude           exclude
     :refer             refer
     :current-ns        current-ns
     :current-ns-sym    current-ns-sym
     :python-namespace  python-namespace
     :this-module       this-module
     :module?           (pymodule? this-module)
     :class?            (pyclass? cls)
     :class-name        (when (pyclass? cls)
                          (module-path-last-string
                           module-name))}))

(defn- extract-public-data
  [{:keys [exclude python-namespace module-name-or-ns]}]
  (let [python-namespace
        (or python-namespace
            (find-ns module-name-or-ns))]
    (->> (ns-publics python-namespace)
         (remove #(exclude (first %)))
         (into {}))))

(defn- reload-python-ns!
  [module-name this-module module-name-or-ns]
  (do
    (remove-ns module-name)
    (reload-module this-module)
    (create-ns module-name-or-ns)))

(defn- create-python-ns!
  [module-name-or-ns]
  (create-ns module-name-or-ns))

(defn ^:private maybe-reload-or-create-ns!
  [{:keys            [reload?
                      this-module
                      module-name
                      module-name-or-ns]
    cls?             :class?
    python-namespace :python-namespace}]

  ;; in the event of '[builtins.list :as python.pylist]
  ;; this function is a no-op
  (cond
    cls?                   nil
    reload?                (reload-python-ns! module-name
                                              this-module
                                              module-name-or-ns)
    (not python-namespace) (create-python-ns! module-name-or-ns)))

(defn enhanced-python-lib-configuration
  [{:keys [python-namespace exclude this-module]
    cls? :class?
    :as   lib-config}]
  ;; in the even of '[builtins.list :as python.pylist]
  ;; or something similar, we do not provide enhanced
  ;; data
  (if cls?
    lib-config
    (let [public-data (extract-public-data lib-config)]
      (merge
       lib-config
       {:public-data   public-data
        :refer-symbols (extract-refer-symbols
                        lib-config
                        public-data)}))))

(defn- bind-py-symbols-to-ns!
  [{:keys [reload?
           python-namespace
           this-module
           module-name-or-ns
           no-arglists?]
    cls?   :class?}]
  (when (and (or reload? (not python-namespace)) (not cls?))
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
          (log/warnf e "Failed to require symbol %s" att-name))))))

(defn- bind-module-ns!
  [{:keys [current-ns-sym
           module-name-or-ns
           class-name-or-ns
           this-module]
    cls? :class?}]

  ;; in event of '[builtins.list :as python.pylist]
  ;; this is a no-op
  (when (not cls?)
    (intern current-ns-sym
            (with-meta module-name-or-ns
              {:doc (doc this-module)})
            this-module)))

(defn- generate-class-namespace-configs
  [{:keys [module-name-or-ns
           this-module
           class-name
           class-name-or-ns
           ]
    cls?  :class?
    :as   lib-config}]
  (letfn [(pyclass-pair? [[attr attr-val]]

            (or

             ;; in the event that something like
             ;; builtins.list was requested,
             ;; we do not want to bind every
             ;; class in the module to the ns, only the
             ;; requested class

             (and cls?
                  (pyclass? attr-val)
                  (and (hasattr attr-val "__name__")
                       (= class-name
                          (py/get-attr
                           attr-val
                           "__name__"))))

             ;; if a module like 'builtins was specified
             ;; without a class like 'builtins.list,
             ;; we bind every class in the module to the module-ns

             (and (not cls?)
                  (pyclass? attr-val))))
          (pyclass-ns-config [[attr attr-val]]
            {:namespace      module-name-or-ns
             :attribute-type :class
             :classname      attr             
             :class-symbol   (symbol attr)
             :class-binding  (or class-name-or-ns
                                 class-name)
             :class          attr-val
             :attributes     ((comp
                               (py/get-attr builtins "dict")
                               vars) attr-val)})
          (pyclass-attribute-fanout
            [{:keys [namespace
                     classname
                     class-symbol
                     class
                     attributes]
              :as   attr-config}]
            (into [attr-config]
                  (for [[attr attr-val] (seq attributes)
                        :let
                        [attr-type
                         (if (and (not (nil? attr-val))
                                  (py/callable?  attr-val))

                           :method
                           :attribute)]]
                    (merge
                     (dissoc attr-config :attributes :type)
                     {:attribute-type attr-type
                      :type           :class-attribute
                      :class-binding  (or class-name-or-ns
                                          class-name)
                      :attribute      attr-val
                      :attribute-name attr}))))]
    (into []
          (comp
           (filter pyclass-pair?)
           (map pyclass-ns-config)
           (map pyclass-attribute-fanout)
           cat)
          (seq (vars this-module)))))

(defmulti class-sort :attribute-type)
(defmethod class-sort :class [_] 0)
(defmethod class-sort :method [_] 1)
(defmethod class-sort :attribute [_] 2)

(defmulti intern-ns-class :attribute-type)

(defmethod intern-ns-class :class
  [{original-namespace :namespace
    cls-name           :classname
    cls-sym            :class-symbol
    cls                :class
    cls-binding        :class-binding
    :as                cls-ns-config}]

  ;; cls-binding percolates down to here in the situation where
  ;; '[builtins.list :as python.pylist] occurs
  
  (let [cls-ns (symbol (str original-namespace
                            "."
                            (or
                             cls-binding
                             cls-sym)))]
    (create-ns cls-ns)
    cls-ns-config))

(defmethod intern-ns-class :method
  [{original-namespace :namespace
    cls-name           :classname
    cls-sym            :class-symbol
    cls                :class
    cls-binding        :class-binding
    method-name        :attribute-name
    method             :attribute
    :as                cls-ns-config}]

  ;; cls-binding percolates down to here in the situation where
  ;; '[builtins.list :as python.pylist] occurs

  (let [cls-ns (symbol (str original-namespace "."
                            (or  cls-binding cls-sym)))]
    (load-py-fn method (symbol method-name)
                cls-ns {})
    cls-ns-config))

(defmethod intern-ns-class :attribute
  [{original-namespace :namespace
    cls-name           :classname
    cls-sym            :class-symbol
    cls                :class
    cls-binding        :class-binding
    attribute-name     :attribute-name
    attribute          :attribute
    :as                cls-ns-config}]

  ;; cls-binding percolates down to here in the situation where
  ;; '[builtins.list :as python.pylist] occurs

  (let [cls-ns (symbol (str original-namespace "."
                            (or  cls-binding cls-sym)))]
    (intern cls-ns (symbol attribute-name) attribute)
    cls-ns-config))

(defn- bind-class-namespaces!
  [{cls?   :class?
    module :module?
    :as    lib-config}]
  (let [class-namespace-configs
        (->>
         (generate-class-namespace-configs
          lib-config)
         (sort-by class-sort))]
    (doseq [cls-namespace-config class-namespace-configs]
      (intern-ns-class cls-namespace-config))))

(defn- intern-public-and-refer-symbols!
  [{:keys [public-data refer-symbols current-ns-sym]}]
  (doseq [[s v] (select-keys public-data refer-symbols)]
    (intern current-ns-sym
            (with-meta s (meta v))
            (deref v))))

(defn preload-python-lib! [req]
  (let [lib-config (python-lib-configuration req)]
    (maybe-reload-or-create-ns! lib-config)
    (bind-py-symbols-to-ns! lib-config)
    (bind-module-ns! lib-config)
    (enhanced-python-lib-configuration lib-config)))

(defn ^:private load-python-lib [req]
  (let [{:keys [supported-flags
                flags
                etc
                reload?
                no-arglists?
                load-ns-classes?
                module-name
                module-name-or-ns
                exclude
                refer
                current-ns
                current-ns-sym
                python-namespace
                this-module
                python-namespace
                public-data
                refer-symbols
                class?
                module?]
         :as   lib-config}
        (preload-python-lib! req)]

    (when (not class?)
      (intern-public-and-refer-symbols! lib-config))

    ;; alpha
    ;; TODO: does not respect reloading or much of the other
    ;;   ..: syntax (yet)
    (when load-ns-classes?
      (bind-class-namespaces! lib-config))))

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

