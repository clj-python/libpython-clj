(ns libpython-clj.require
  (:refer-clojure :exclude [fn? doc])
  (:require [libpython-clj.python :as py]
            [libpython-clj.metadata :as pymeta]
            [clojure.datafy :refer [datafy nav]]
            ;;Binds datafy/nav to python objects
            [libpython-clj.metadata]
            [clojure.tools.logging :as log]))

;; for hot reloading multimethod in development
(ns-unmap 'libpython-clj.require 'intern-ns-class)


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
                                                  :* :all :bind-ns]))]
    (when-let [missing-flags (->> reqs
                                  (filter #(and (not (total-flags %))
                                                (keyword? %)))
                                  seq)]
      (throw (Exception. (format "Unsupported flags: %s"
                                 (set missing-flags))))))
  ;;Loop through reqs.  If a keyword is found and it is a supported flag,
  ;;see if the next thing is a boolean with a default to true.
  ;;If the flag is enabled (as false could be passed in), conj (or disj) to flag set
  ;;Return reqs minus flags and booleans.
  (loop [reqs reqs
         retval-reqs []
         retval-flags #{}]
    (if (seq reqs)
      (let [next-item (first reqs)
            reqs (rest reqs)
            [bool-flag reqs]
            (if (and (supported-flags next-item)
                     (boolean? (first reqs)))
              [(first reqs) (rest reqs)]
              [true reqs])
            retval-flags (if (supported-flags next-item)
                           (if bool-flag
                             (conj retval-flags next-item)
                             (disj retval-flags next-item))
                           retval-flags)
            retval-reqs (if (not (supported-flags next-item))
                          (conj retval-reqs next-item)
                          retval-reqs)]
        (recur reqs retval-reqs retval-flags))
      retval-flags)))


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


(defn- do-require-python
  [reqs-vec]
  (let [[module-name & etc] reqs-vec
        supported-flags     #{:reload :no-arglists :bind-ns}
        flags               (parse-flags supported-flags etc)
        etc                 (->> etc
                                 (remove supported-flags)
                                 (remove boolean?))
        _                   (when-not (= 0 (rem (count etc) 2))
                              (throw (Exception. "Must have even number of entries")))
        etc                 (->> etc (partition-all 2)
                                 (map vec)
                                 (into {}))
        reload?             (:reload flags)
        no-arglists?        (:no-arglists flags)
        bind-ns?            (:bind-ns flags)
        alias-name          (:as etc)
        exclude             (into #{} (:exclude etc))

        refer-data          (cond
                              (= :all (:refer etc)) #{:all}
                              (= :* (:refer etc))   #{:*}
                              :else                 (into #{} (:refer etc)))
        pyobj               (pymeta/path->py-obj (str module-name) :reload? reload?)
        existing-py-ns?     (find-ns module-name)]
    (create-ns module-name)

    (when bind-ns?
      (let [import-name (or  (not-empty (str alias-name))
                             (str module-name))
            ns-dots (re-find #"[.]" import-name)]
        (when (not (zero? (count ns-dots)))
          (throw (Exception. (str "Cannot have periods in module/class"
                                  "name. Please :alias "
                                  import-name
                                  " to something without periods."))))        
        (intern
         (symbol (str *ns*))
         (symbol import-name)
         pyobj)))
    
    (when (or (not existing-py-ns?) reload?)
      (pymeta/apply-static-metadata-to-namespace! module-name (datafy pyobj)
                                                  :no-arglists? no-arglists?))
    (when-let [refer-symbols (->> (extract-refer-symbols {:refer       refer-data
                                                          :this-module pyobj}
                                                         (ns-publics
                                                          (find-ns module-name)))
                                  seq)]
      (refer module-name :exclude exclude :only refer-symbols))
    (when alias-name
      (alias alias-name module-name))))


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

   If you would like to bind the Python module to the namespace, use
   the :bind-ns flag.

   (require-python '[requests :bind-ns true]) or
   (require-python '[requests :bind-ns])

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
    (doseq [req reqs] (require-python req))
    (symbol? reqs)
    (require-python (vector reqs))
    (vector? reqs)
    (do-require-python reqs)
    :else
    (throw (Exception. "Invalid argument: %s" reqs))))

