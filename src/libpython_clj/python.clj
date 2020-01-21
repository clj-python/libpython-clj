(ns libpython-clj.python
  (:require [tech.parallel.utils :refer [export-symbols]]
            [libpython-clj.python.interop :as pyinterop]
            [libpython-clj.python.interpreter :as pyinterp]
            [libpython-clj.python.object :as pyobj]
            [libpython-clj.python.bridge :as pybridge]
            [libpython-clj.jna :as libpy]
            ;;Protocol implementations purely for nd-ness
            [libpython-clj.python.np-array]
            [tech.jna :as jna])
  (:import [com.sun.jna Pointer]
           [com.sun.jna.ptr PointerByReference]
           [java.lang.reflect Field]
           [java.io Writer]
           [libpython_clj.jna PyObject DirectMapped
            CFunction$KeyWordFunction
            CFunction$TupleFunction
            CFunction$NoArgFunction]))


(set! *warn-on-reflection* true)


(export-symbols libpython-clj.python.protocols
                python-type
                dir
                att-type-map
                get-attr
                has-attr?
                set-attr!
                callable?
                has-item?
                get-item
                set-item!
                call
                call-kw
                call-attr
                call-attr-kw
                len
                as-map
                as-list
                as-tensor)


(export-symbols libpython-clj.python.object
                ->py-float
                ->py-long
                ->py-string
                ->python
                ;;Used when you are returning a value from a function.
                ->python-incref
                ->jvm
                make-tuple-fn
                make-tuple-instance-fn
                create-class
                is-instance?
                hash-code
                equals?)


(defmacro stack-resource-context
  "Create a stack-based resource context.  All python objects allocated within this
  context will be released at the termination of this context.
  !!This means that no python objects can escape from this context!!
  You must use copy semantics (->jvm) for anything escaping this context.
  Furthermore, if you are returning generic python objects you may need
  to call (into {}) or something like that just to ensure that absolutely
  everything is copied into the jvm."
  [& body]
  `(pyobj/stack-resource-context
    ~@body))


(defmacro with-gil
  "Capture the gil for an extended amount of time.  This can greatly speed up
  operations as the mutex is captured and held once as opposed to find grained
  grabbing/releasing of the mutex."
  [& body]
  `(pyinterp/with-gil
     ~@body))


(defmacro with-gil-stack-rc-context
  "Capture the gil, open a resource context.  The resource context is released
  before the gil is leading to much faster resource collection.  See documentation
  on `stack-resource-context` for multiple warnings; the most important one being
  that if a python object escapes this context your program will eventually, at
  some undefined point in the future crash.  That being said, this is the recommended
  pathway to use in production contexts where you want defined behavior and timings
  related to use of python."
  [& body]
  `(with-gil
     (stack-resource-context
      ~@body)))


(defn set-attrs!
  "Set a sequence of [name value] attributes.
  Returns item"
  [item att-seq]
  (with-gil
    (doseq [[k v] att-seq]
      (set-attr! item k v)))
  item)


(defn set-items!
  "Set a sequence of [name value].
  Returns item"
  [item item-seq]
  (with-gil
    (doseq [[k v] item-seq]
      (set-item! item k v)))
  item)


(export-symbols libpython-clj.python.interop
                libpython-clj-module-name
                create-bridge-from-att-map)


(export-symbols libpython-clj.python.bridge
                args->pos-kw-args
                cfn
                afn
                as-jvm
                as-python
                as-python-incref ;; Used when returning a value from a function to python.
                ->numpy
                as-numpy)


(defn ->py-dict
  "Create a python dictionary"
  [item]
  (-> (pyobj/->py-dict item)
      (as-jvm)))


(defn ->py-list
  "Create a python list"
  [item]
  (-> (pyobj/->py-list item)
      (as-jvm)))


(defn ->py-tuple
  "Create a python tuple"
  [item]
  (-> (pyobj/->py-tuple item)
      (as-jvm)))


(defn ->py-fn
  "Make a python function.  If clojure function is passed in the arguments are
  marshalled from python to clojure, the function called, and the return value will be
  marshalled back."
  [item]
  (-> (pyobj/->py-fn item)
      (as-jvm)))


(defn run-simple-string
  "Run a string expression returning a map of
  {:globals :locals :result}.
  This uses the global __main__ dict under the covers so it matches the behavior
  of the cpython implementation with the exception of returning the various maps
  used.

  Note this will never return the result of the expression:
  https://mail.python.org/pipermail/python-list/1999-April/018011.html

  Globals, locals may be provided but are not necessary.

  Implemented in cpython as:

    PyObject *m, *d, *v;
    m = PyImport_AddModule(\"__main__\");
    if (m == NULL)
        return -1;
    d = PyModule_GetDict(m);
    v = PyRun_StringFlags(command, Py_file_input, d, d, flags);
    if (v == NULL) {
        PyErr_Print();
        return -1;
    }
    Py_DECREF(v);
    return 0;"
  [program & {:keys [globals locals]}]
  (->> (pyinterop/run-simple-string program :globals globals :locals locals)
       (map (fn [[k v]]
              [k (as-jvm v)]))
       (into {})))


(defn run-string
  "Wrapper around the python c runtime PyRun_String method.  This requires you to
  understand what needs to be in the globals and locals dict in order for everything
  to work out right and for this reason we recommend run-simple-string."
  [program & {:keys [globals locals]}]
  (->> (pyinterop/run-string program :globals globals :locals locals)
       (map (fn [[k v]]
              [k (as-jvm v)]))
       (into {})))


(defn import-module
  "Import a python module.  Returns a bridge"
  [modname]
  (-> (pyinterop/import-module modname)
      (as-jvm)))


(defn add-module
  "Add a python module.  Returns a bridge"
  [modname]
  (-> (pyinterop/add-module modname)
      (as-jvm)))


(defn module-dict
  "Get the module dictionary.  Returns bridge."
  [module]
  (-> (pyinterop/module-dict module)
      as-jvm))


(defn initialize!
  "Initialize the python library.  If library path is provided, then the python
  :library-path Library path of the python library to use.
  :program-name - optional but will show up in error messages from python.
  :no-io-redirect - there if you don't want python stdout and stderr redirection
     to *out* and *err*."
  [& {:keys [program-name
             library-path
             python-home
             no-io-redirect?
             python-executable]}]
  (when-not @pyinterp/main-interpreter*
    (pyinterp/initialize! :program-name program-name
                          :library-path library-path
                          :python-home python-home
                          :python-executable python-executable)
    ;;setup bridge mechansim and io redirection
    (pyinterop/register-bridge-type!)
    (when-not no-io-redirect?
      (pyinterop/setup-std-writer #'*err* "stderr")
      (pyinterop/setup-std-writer #'*out* "stdout")))
  :ok)


(defn ptr-refcnt
  [item]
  (-> (libpy/as-pyobj item)
      (libpython_clj.jna.PyObject. )
      (.ob_refcnt)))


(defn finalize!
  "Finalize the interpreter.  You probably shouldn't call this as it destroys the
  global interpreter and reinitialization is unsupported cpython."
  []
  (pyinterp/finalize!))


(defn python-pyerr-fetch-error-handler
  "Utility code used in with macro"
  []
  (let [ptype# (PointerByReference.)
        pvalue# (PointerByReference.)
        ptraceback# (PointerByReference.)
        _# (libpy/PyErr_Fetch ptype# pvalue# ptraceback#)
        ptype# (-> (jna/->ptr-backing-store ptype#)
                   (pyobj/wrap-pyobject true))
        pvalue# (-> (jna/->ptr-backing-store pvalue#)
                    (pyobj/wrap-pyobject true))
        ptraceback# (-> (jna/->ptr-backing-store ptraceback#)
                        (pyobj/wrap-pyobject true))]
    ;;We own the references so they have to be released.
    (throw (ex-info "python error in flight"
                    {:ptype ptype#
                     :pvalue pvalue#
                     :ptraceback ptraceback#}))))


(defn with-exit-error-handler
  "Utility code used in with macro"
  [with-var error]
  (let [einfo (ex-data error)]
    (if (every? #(contains? einfo %) [:ptype :pvalue :ptraceback])
      (let [{ptype :ptype
             pvalue :pvalue
             ptraceback :ptraceback} einfo
            suppress-error? (call-attr with-var "__exit__"
                                       ptype
                                       pvalue
                                       ptraceback)]
        (when (and ptype pvalue ptraceback
                   (not suppress-error?))
          (do
            ;;Manual incref here because we cannot detach the object
            ;;from our gc decref hook added during earlier pyerr-fetch handler.
            (libpy/Py_IncRef ptype)
            (libpy/Py_IncRef pvalue)
            (libpy/Py_IncRef ptraceback)
            (libpy/PyErr_Restore ptype pvalue ptraceback)
            (pyinterp/check-error-throw))))
      (do
        (call-attr with-var "__exit__" nil nil nil)
        (throw error)))))


(defmacro with
  "Support for the 'with' statement in python:
  (py/with [item (py/call-attr testcode-module \"WithObjClass\" true fn-list)]
                    (py/call-attr item \"doit_err\"))"
  [bind-vec & body]
  (when-not (= 2 (count bind-vec))
    (throw (Exception. "Bind vector must have 2 items")))
  (let [varname (first bind-vec)]
    `(with-gil
       (let [~@bind-vec]
         (with-bindings
           {#'libpython-clj.python.interpreter/*python-error-handler*
            python-pyerr-fetch-error-handler}
           (call-attr ~varname "__enter__")
           (try
             (let [retval#
                   (do
                     ~@body)]
               (call-attr ~varname "__exit__" nil nil nil)
               retval#)
             (catch Throwable e#
               (with-exit-error-handler ~varname e#))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defmacro a$
  "Call an attribute of an object.  Similar calling conventions to afn except:
  Keywords must be compile time constants.  So this won't work with 'apply'.  On the
  other hand, building the positional and kw argmaps happens at compile time as
  opposed to at runtime.  The attr name can be a symbol.

  DEPRECATION POSSIBLE - use $a."
  [item attr & args]
  (let [[pos-args kw-args] (args->pos-kw-args args)]
    `(call-attr-kw ~item ~(pybridge/key-sym-str->str attr)
                   ~pos-args ~kw-args)))


(defmacro c$
  "Call an object.  Similar calling conventions to cfn except:
  Keywords must be compile time constants.  So this won't work with 'apply'.  On the
  other hand, building the positional and kw argmaps happens at compile time as
  opposed to at runtime.

  DEPRECATION POSSIBLE - use $c."
  [item & args]
  (let [[pos-args kw-args] (args->pos-kw-args args)]
    `(call-kw ~item ~pos-args ~kw-args)))



(defmacro $a
  "Call an attribute of an object.  Similar calling conventions to afn except:
  Keywords must be compile time constants.  So this won't work with 'apply'.  On the
  other hand, building the positional and kw argmaps happens at compile time as
  opposed to at runtime.  The attr name can be a symbol."
  [item attr & args]
  `(a$ ~item ~attr ~@args))


(defmacro $c
  "Call an object.  Similar calling conventions to cfn except:
  Keywords must be compile time constants.  So this won't work with 'apply'.  On the
  other hand, building the positional and kw argmaps happens at compile time as
  opposed to at runtime."
  [item & args]
  `(c$ ~item ~@args))


(defmacro $.
  "Get the attribute of an object."
  [item attname]
  `(get-attr ~item ~(pybridge/key-sym-str->str attname)))


(defmacro $..
  "Get the attribute of an object.  If there are extra args, apply successive
  get-attribute calls to the arguments."
  [item attname & args]
  `(-> (get-attr ~item ~(pybridge/key-sym-str->str attname))
       ~@(->> args
              (map (fn [arg]
                     `(get-attr ~(pybridge/key-sym-str->str arg)))))))


(defmacro import-as
  "Import a module and assign it to a var.  Documentation is included."
  [module-path varname]
  `(let [~'mod-data (import-module ~(name module-path))]
     (def ~varname (import-module ~(name module-path)))
     (alter-meta! #'~varname assoc :doc (get-attr ~'mod-data "__doc__"))
     #'~varname))


(defmacro from-import
  "Support for the from a import b,c style of importing modules and symbols in python.
  Documentation is included."
  [module-path item & args]
  `(do
     (let [~'mod-data (import-module ~(name module-path))]
       ~@(map (fn [varname]
                `(let [~'var-data (get-attr ~'mod-data ~(name varname))]
                   (def ~varname ~'var-data)
                   (alter-meta! #'~varname assoc :doc (get-attr ~'var-data "__doc__"))
                   #'~varname))
              (concat [item] args)))))


(defmacro py.-
  "Class/object getter syntax.  (py.- obj attr) is equivalent to
  Python's obj.attr syntax."
  [x arg]
  (list #'$. x arg))


(defmacro py.
  "Class/object method syntax.  (py. obj method arg1 arg2 ... argN)
  is equivalent to Python's obj.method(arg1, arg2, ..., argN) syntax."
  [x & args]
  (list* (into (vector #'$a x) args)))


(defn ^:private handle-pydotdot
  ([x form]
   (if (list? form)
     (let [form-data (vec form)
           [instance-member & args] form-data
           symbol-str (str instance-member)]
       (if (clojure.string/starts-with? symbol-str "-")
         (list #'py.- x (symbol (subs symbol-str 1 (count symbol-str))))
         (list* (into (vector #'py. x instance-member) args))))
     (handle-pydotdot x (list form))))
  ([x form & more]
   (apply handle-pydotdot (handle-pydotdot x form) more)))


(defmacro py..
  "Extended accessor notation, similar to the `..` macro in Clojure.

  (require-python 'sys)
  (py.. sys -path (append \"/home/user/bin\"))

  is equivalent to Python's

  import sys
  sys.path.append('/home/user/bin')"
  [x & args]
  (apply handle-pydotdot x args))
