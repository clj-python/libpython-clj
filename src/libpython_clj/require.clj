(ns libpython-clj.require
  (:refer-clojure :exclude [fn? doc])
  (:require [libpython-clj.python :as py]))

(def ^:private builtins (py/import-module "builtins"))

(def ^:private inspect (py/import-module "inspect"))

(def ^:private argspec (py/get-attr inspect "getfullargspec"))

(def ^:private py-source (py/get-attr inspect "getsource"))

(def ^:private types (py/import-module "types"))

(def ^:private fn-type (py/get-attr types "FunctionType"))

(def ^:private method-type (py/get-attr types "MethodType"))

(def ^:private isinstance? (py/get-attr builtins "isinstance"))

(def ^:private fn? #(isinstance? % fn-type))

(def ^:private method? #(isinstance? % method-type))

(def ^:private doc #(try
                      (py/get-attr % "__doc__")
                      (catch Exception e
                        nil)))

(def ^:private importlib (py/import-module "importlib"))

(def ^:private reload-module (py/get-attr importlib "reload"))

(def ^:priviate import-module (py/get-attr importlib "import_module"))

(def ^{:private false :public true}
  get-pydoc doc)

(def ^:private vars (py/get-attr builtins "vars"))

(defn ^:private py-fn-argspec [f]
  (let [spec (argspec f)]
    {:args           (py/->jvm (py/get-attr spec "args"))
     :varargs        (py/->jvm (py/get-attr spec "varargs"))
     :varkw          (py/->jvm (py/get-attr spec "varkw"))
     :defaults       (py/->jvm (py/get-attr spec "defaults"))
     :kwonlyargs     (py/->jvm  (py/get-attr spec "kwonlyargs"))
     :kwonlydefaults (py/->jvm  (py/get-attr spec "kwonlydefaults"))
     :annotations    (py/->jvm  (py/get-attr spec "annotations"))}))

(defn ^:private py-class-argspec [class]
  (let [constructor (py/get-attr class "__init__")]
    (py-fn-argspec constructor)))

(defn pyargspec [x]
  (cond
    (fn? x) (py-fn-argspec x)
    (method? x) (py-fn-argspec x)
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
   (println argspec)
   (println defaults)
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
           (nil? varargs)       (list '& [or-map])
           :else                (list '& [(symbol varargs)
                                          kwargs-map]))

         arglist (concat (list* pos-args) opt-args)]
     (let [arglists  (conj res arglist)
           defaults' (if (not-empty defaults) (pop defaults) [])
           argspec'  (update argspec :args
                             (fn [args]
                               (if (not-empty args)
                                 (pop args)
                                 args)))]

       (if (and (empty? defaults) (empty? defaults'))
         arglists
         (recur argspec defaults' arglists))))))

(defn ^:private load-py-fn [f fn-name fn-module-name-or-ns]
  (let [fn-argspec   (pyargspec f)
        fn-docstr    (get-pydoc f)
        fn-ns        (symbol (str fn-module-name-or-ns))
        fn-sym       (symbol fn-name)
        fn-arglists (pyarglists fn-argspec)]
    (intern fn-ns 
            (with-meta fn-sym 
              (merge
               fn-argspec
               {:doc      fn-docstr
                :arglists fn-arglists
                :name     fn-name}))
            f)))

(defn ^:private load-python-lib [req]
  (let [supported-flags     #{:reload}
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
        this-module         (import-module (str module-name))
        module-name-or-ns   (:as etc module-name)
        exclude             (into #{} (:exclude etc))
        refer          (cond
                         (= :all (:refer etc)) #{:all}
                         (= :* (:refer etc))   #{:*}
                         :else                 (into
                                                #{}
                                                (:refer etc)))
        current-ns     *ns*
        current-ns-sym (symbol (str current-ns))];; if the current namespace is already loaded, unless
    ;; the :reload flag is specified, this will be a no-op
    (when (not (and (find-ns module-name-or-ns)
                    (not reload?)));; :reload behavior

      ;; TODO: should we track things referred into the existing
      ;;   ..: *ns* with an atom and clear them on :reload?

      (when reload?
        (remove-ns module-name)
        (reload-module this-module))

      ;; bind the python module to its symbolic name
      ;; in the current namespace
      (intern current-ns-sym module-name-or-ns this-module)

      ;; create namespace for module and bind python
      ;; values to namespace symbols
      (doseq [[k pyfn?] (seq (py/as-jvm (vars  this-module)))]
        (try
          (load-py-fn pyfn? (symbol k) module-name-or-ns)
          (catch Exception e
            (try
              (let [ns     module-name-or-ns
                    symbol (symbol k)]
                (in-ns ns)
                (intern ns symbol pyfn?))
              (finally
                (in-ns current-ns-sym))))))

      ;; behavior for [.. :refer :all], [.. :refer [...]], and
      ;; [.. :refer :*]
      ;; TODO: code is a bit repetitive maybe
      (cond
        ;; include everything into the current namespace,
        ;; ignore __all__ directive
        (refer :all)
        (doseq
            [[k pyfn?]
             (seq (py/as-jvm (vars  this-module)))
             :when (not (exclude (symbol k)))]
          (try
            (load-py-fn pyfn? (symbol k) current-ns-sym)
            (catch Exception e
              (let [symbol (symbol k)]
                (intern *ns* symbol pyfn?)))))
        ;; only include that specfied by __all__ attribute
        ;; of python module if specified, else same as :all
        (refer :*)
        (let [hasattr (py/get-attr builtins "hasattr")
              getattr (py/get-attr builtins "getattr")]
          (if (hasattr this-module "__all__")
            (doseq
                [[k pyfn?]
                 (for [item-name (getattr this-module "__all__")]
                   [(symbol item-name)
                    (getattr this-module item-name)])
                 :when (not (exclude (symbol k)))]
              (try
                (load-py-fn pyfn? (symbol k) current-ns-sym)
                (catch Exception e
                  (let [symbol (symbol k)]
                    (intern *ns* symbol pyfn?)))))
            (doseq
                [[k pyfn?]
                 (seq (py/as-jvm (vars  this-module)))
                 :when (not (exclude (symbol k)))]
              (try
                (load-py-fn pyfn? (symbol k) current-ns-sym)
                (catch Exception e
                  (let [symbol (symbol k)]
                    (intern *ns* symbol pyfn?)))))))

        ;; [.. :refer [..]] behavior
        :else
        (doseq [r    refer
                :let [pyfn? (py/get-attr this-module (str r))]]
          (if (or (fn? pyfn?) (method? pyfn?))
            (load-py-fn pyfn?)
            (intern current-ns-sym r pyfn?)))))))

(defn require-python [reqs]
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

  (cond
    (list? reqs)
    (doseq [req (vec reqs)] (require-python req))
    (symbol? reqs)
    (load-python-lib (vector reqs))
    (vector? reqs)
    (load-python-lib reqs)))

(comment
  (require-python '([clojure :refer [parenthesis]] __future__))
  (py/set-attr! __future__ "braces" parenthesis))

