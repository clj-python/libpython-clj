(ns libpython-clj2.python
  "Python bindings for Clojure.  This library dynamically finds the installed
  python, loads the shared library and allows Clojure users to use Python modules
  as if they were Clojure namespaces.


Example:

```clojure
user> (require '[libpython-clj2.python :as py])
nil
user> (py/initialize!)
;;  ... (logging)
:ok
user> (def np (py/import-module \"numpy\"))
#'user/np
user> (py/py. np linspace 2 3 :num 10)
[2.         2.11111111 2.22222222 2.33333333 2.44444444 2.55555556
 2.66666667 2.77777778 2.88888889 3.        ]
```"
  (:require [libpython-clj2.python.info :as py-info]
            [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.fn :as py-fn]
            [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.class :as py-class]
            [libpython-clj2.python.with :as py-with]
            [libpython-clj2.python.dechunk-map :refer [dechunk-map]]
            [libpython-clj2.python.copy :as py-copy]
            [libpython-clj2.python.bridge-as-jvm :as py-bridge-jvm]
            [libpython-clj2.python.bridge-as-python]
            [libpython-clj2.python.io-redirect :as io-redirect]
            [libpython-clj2.python.gc :as pygc]
            [libpython-clj2.python.windows :as win]
            [tech.v3.datatype.ffi :as dtype-ffi]
            [tech.v3.datatype.errors :as errors]
            [clojure.tools.logging :as log]
            clojure.edn)
  (:import [java.util Map List]
           [clojure.lang IFn]))


(set! *warn-on-reflection* true)

(defn- no-op [])

(defn initialize!
    "Initialize the python library.  If library path is not provided, then the system
  attempts to execute a simple python program and have python return system info.

  Note: all of the options passed to `initialize!` may now be provided in 
  a root-level `python.edn` file.  Example:

  ```
  ;; python.edn
  {:python-executable   \"/usr/bin/python3.7\"
   :python-library-path \"/usr/lib/libpython3.7m.so\"
   :python-home         \"/usr/lib/python3.7\"
   :python-verbose      true}
  ```
  or, using a local virtual environment:
  ```
  ;; python.edn
  {:python-executable   \"env/bin/python\"}
  ```

  Additionaly the file can contain two keys which can can refer to custom hooks
  to run code just before and just after python is initialised.
  Typical use case for this is to setup / verify the python virtual enviornment
  to be used.

  ```
  :pre-initialize-fn my-ns/my-venv-setup-fn!
  :post-initialize-fn my-ns/my-venv-validate-fn!

  ```

  A :pre-initialize-fn could for example shell out and setup a python
  virtual enviornment.

  The :post-initialize-fn can use all functions from ns `libpython-clj2.python`
  as libpython-clj is initialised alreday andc ould for example be used to validate
  that later needed libraries can be loaded via calling `import-module`.

  The file MUST be named `python.edn` and be in the root of the classpath.
  With a `python.edn` file in place, the `initialize!` function may be called
  with no arguments and the options will be read from the file. If arguments are 
  passed to `initialize!` then they will override the values in the file.

  Returns either `:ok` in which case the initialization completed successfully or
  `:already-initialized` in which case we detected that python has already been
  initialized via `Py_IsInitialized` and we do nothing more.

  Options:

  * `:library-path` - Library path of the python library to use.
  * `:program-name` - Optional -- will show up in error messages from python.
  * `:no-io-redirect?` - True if you don't want python stdout and stderr redirection
     to *out* and *err*.
  * `:python-executable` - The python executable to use to find system information.
  * `:python-home` - Python home directory.  The system first uses this variable, then
     the environment variable PYTHON_HOME, and finally information returned from
     python system info.
  * `:signals?` - defaults to false - true if you want python to initialized signals.
     Be aware that the JVM itself uses quite a few signals - SIGSEGV, for instance -
     during it's normal course of operation.  For more information see:
       * [used signals](https://docs.oracle.com/javase/10/troubleshoot/handle-signals-and-exceptions.htm#JSTGD356)
       * [signal-chaining](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/signal-chaining.html)"
  [& {:keys [windows-anaconda-activate-bat
             library-path
             no-io-redirect?]
      :as options}]
  (if-not (and (py-ffi/library-loaded?)
               (= 1 (py-ffi/Py_IsInitialized)))
    (let [python-edn-opts (-> (try (slurp "python.edn")
                                   (catch java.io.FileNotFoundException _ "{}"))
                              clojure.edn/read-string)
          _ ((requiring-resolve (get python-edn-opts :pre-initialize-fn 'libpython-clj2.python/no-op)))
          options (merge python-edn-opts options)
          info (py-info/detect-startup-info options)
          _ (log/infof "Startup info %s" info)
          _ (when-let [lib-path (:java-library-path-addendum
                                 options (:java-library-path-addendum info))]
              (log/infof "Prefixing java library path: %s" lib-path)
              (py-ffi/append-java-library-path! lib-path))
          libname (->> (concat (when library-path [library-path]) (:libnames info))
                       (dechunk-map identity)
                       (map dtype-ffi/find-library)
                       (remove nil?)
                       (first))]
      (errors/when-not-errorf
       libname
       "Failed to find a valid python library!")
      (log/infof "Loading python library: %s" libname)
      (py-ffi/initialize!
       libname (:python-home info)
       (assoc options
              :program-name (:program-name options (:executable info))
              :python-home (:python-home options (:python-home info))
              :java-library-path-addendum (:java-library-path-addendum
                                           options
                                           (:java-library-path-addendum info))))
      (let [gilstate (py-ffi/lock-gil)]
        (try

          (when-not (nil? windows-anaconda-activate-bat)
            (win/setup-windows-conda! windows-anaconda-activate-bat
                                      py-ffi/run-simple-string))

          (when-not no-io-redirect?
            (io-redirect/redirect-io!))
          (finally
            (py-ffi/unlock-gil gilstate))))
      ((requiring-resolve (get python-edn-opts :post-initialize-fn 'libpython-clj2.python/no-op)))
      :ok)
    :already-initialized))


(defmacro stack-resource-context
  "Create a stack-based resource context.  All python objects allocated within this
  context will be released at the termination of this context.
  !!This means that no python objects can escape from this context!!
  You must use copy semantics (->jvm) for anything escaping this context.
  Furthermore, if you are returning generic python objects you may need
  to call (into {}) or something like that just to ensure that absolutely
  everything is copied into the jvm."
  [& body]
  `(pygc/with-stack-context
     ~@body))


(defmacro with-gil
  "Capture the gil for an extended amount of time.  This can greatly speed up
  operations as the mutex is captured and held once as opposed to fine grained
  grabbing/releasing of the mutex."
  [& body]
  `(py-ffi/with-gil
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
  `(py-ffi/with-gil
     (pygc/with-stack-context
       ~@body)))


(defmacro with-manual-gil
  "When running with -Dlibpython_clj.manual_gil=true, you need to wrap all accesses to
  the python runtime with this locker.  This includes calls to require-python or any other
  pathways.

```clojure
  (with-manual-gil
    ...)
```
  "
  [& body]
  `(with-open [locker# (py-ffi/manual-gil-locker)]
     ~@body))


(defmacro with-manual-gil-stack-rc-context
  "When running with -Dlibpython_clj.manual_gil=true, you need to wrap all accesses to
  the python runtime with this locker.  This includes calls to require-python or any other
  pathways.  This macro furthermore defines a stack-based gc context to immediately release
  objects when the stack frame exits."
  [& body]
  `(with-manual-gil
     (pygc/with-stack-context
       ~@body)))


(declare ->jvm)

(defn ^:no-doc in-py-ctx
  [^java.util.function.Supplier supplier]
  (with-gil-stack-rc-context
    (-> (.get supplier)
        (->jvm))))


(defn import-module
  "Import a python module.  Module entries can be accessed via get-attr."
  [modname]
  (with-gil
    (if-let [mod (py-ffi/PyImport_ImportModule modname)]
      (-> (py-ffi/track-pyobject mod)
          (py-base/as-jvm))
      (py-ffi/check-error-throw))))


(defn add-module
  "Add a python module.  This can create a module if it doesn't exist."
  [modname]
  (with-gil
    (-> (py-ffi/PyImport_AddModule modname)
        (py-ffi/incref-track-pyobject)
        (py-base/as-jvm))))


(defn module-dict
  "Get the module dictionary."
  [mod]
  (with-gil
    (-> (py-ffi/PyModule_GetDict mod)
        (py-ffi/incref-track-pyobject)
        (py-base/as-jvm))))


(defn dir
  [pyobj]
  (with-gil (py-proto/dir pyobj)))


(defn call-attr
  "Call an attribute on a python object using only positional arguments"
  [pyobj attname & args]
  (with-gil (py-fn/call-attr pyobj attname args)))


(defn call-attr-kw
  "Call an attribute passing in both positional and keyword arguments."
  [pyobj attname args kw-list]
  (with-gil (py-fn/call-attr-kw pyobj attname args kw-list py-base/as-python)))

(defn get-attr
  "Get an attribute from a python object"
  [pyobj attname]
  (with-gil (py-proto/get-attr pyobj attname)))


(defn set-attr!
  "Set an attribute on a python object.  Returns pyobj."
  [pyobj attname attval]
  (with-gil (py-proto/set-attr! pyobj attname attval))
  pyobj)


(defn set-attrs!
  "Set a sequence of [name value] attributes.  Returns pyobj."
  [pyobj att-seq]
  (with-gil (doseq [[k v] att-seq] (set-attr! pyobj k v)))
  pyobj)


(defn has-attr?
  "Return true if this python object has this attribute."
  [pyobj att-name]
  (py-proto/has-attr? pyobj att-name))


(defn get-item
  "Get an item from a python object using  __getitem__"
  [pyobj item-name]
  (with-gil (py-proto/get-item pyobj item-name)))


(defn set-item!
  "Set an item on a python object using  __setitem__"
  [pyobj item-name item-val]
  (with-gil (py-proto/set-item! pyobj item-name item-val))
  pyobj)


(defn has-item?
  "Return true if the python object has an item.  Calls __hasitem__."
  [pyobj item-name]
  (with-gil (py-proto/has-item? pyobj item-name)))


(defn set-items!
  "Set a sequence of [name value]. Returns pyobj"
  [pyobj item-seq]
  (with-gil (doseq [[k v] item-seq] (set-item! pyobj k v)))
  pyobj)


(defn ->python
  "Copy a jvm value into a python object"
  [v]
  (py-ffi/with-gil (py-base/->python v)))


(defn as-python
  "Bridge a jvm value into a python object"
  [v]
  (py-ffi/with-gil (py-base/as-python v)))


(defn ->jvm
  "Copy a python value into java datastructures"
  [v & [opts]]
  (py-ffi/with-gil (py-base/->jvm v opts)))


(defn as-jvm
  "Copy a python value into java datastructures"
  [v & [opts]]
  (py-ffi/with-gil (py-base/as-jvm v opts)))


(defn as-map
  "Make a python object appear as a map of it's items"
  ^Map [pobj]
  (py-bridge-jvm/generic-python-as-map (delay pobj)))


(defn as-list
  "Make a python object appear as a list"
  ^List [pobj]
  (py-bridge-jvm/generic-python-as-list (delay pobj)))


(defn python-type
  "Get the type (as a keyword) of a python object"
  [v]
  (py-ffi/with-gil (py-proto/python-type v)))


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


(defn is-instance?
  "Return true if inst is an instance of cls.  Note that arguments
  are reversed as compared to `instance?`"
  [py-inst py-cls]
  (py-ffi/with-gil
    (let [retval (long (py-ffi/PyObject_IsInstance py-inst py-cls))]
      (case retval
        0 false
        1 true
        (py-ffi/check-error-throw)))))


(defn callable?
  "Return true if python object is callable."
  [pyobj]
  (cond
    (instance? IFn pyobj)
    true
    (dtype-ffi/convertible-to-pointer? pyobj)
    (py-ffi/with-gil
      (let [retval (long (py-ffi/PyCallable_Check pyobj))]
        (case retval
          0 false
          1 true
          (py-ffi/check-error-throw))))
    :else
    false))


(defn ->py-list
  "Copy the data into a python list"
  [v]
  (py-ffi/with-gil (-> (py-copy/->py-list v) (as-jvm))))


(defn ->py-tuple
  "Copy v into a python tuple"
  [v]
  (py-ffi/with-gil (-> (py-copy/->py-tuple v) (as-jvm))))


(defn ->py-dict
  "Copy v into a python dict"
  [v]
  (py-ffi/with-gil (-> (py-copy/->py-dict v) (as-jvm))))


(defn run-simple-string
  "Run a string expression returning a map of
  {:globals :locals}.
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
  (->> (py-ffi/run-simple-string program :globals globals :locals locals)
       (map (fn [[k v]]
              [k (py-base/as-jvm v)]))
       (into {})))


(defn make-callable
  "Make a python callable object from a clojure function.  This is called for you
  if you use `as-python` on an implementation of IFn.

Options:
  * `:arg-converter` - Function called for each function argument before your ifn
     gets access to it.  Defaults to `->jvm`.
  * `:result-converter` - Function called on return value before it gets returned to
     python.  Must return a python object.  Defaults to `->python`; the result will
     get an extra incref before being returned to Python to account for the implied
     tracking of `as-python` or `->python`.
  * `:name` - Name of the python method.  This will appear in stack traces.
  * `:doc` - documentation for method."
  ([ifn options]
   (py-fn/make-tuple-fn ifn options))
  ([ifn] (make-callable ifn nil)))


(defn ^:no-doc make-tuple-fn
  "Deprecated - use make-callable"
  [ifn & {:as options}]
  (make-callable ifn options))


(defn make-instance-fn
  "Make an callable instance function - a function which will be passed the 'this'
  object as it's first argument.  In addition, this function calls `make-callable`
  with a `arg-converter` defaulted to `as-jvm`.  See documentation for
  [[libpython-clj2.python.class/make-instance-fn."
  ([ifn options] (py-class/make-tuple-instance-fn ifn options))
  ([ifn] (make-instance-fn ifn nil)))


(defn make-kw-instance-fn
  "Make an kw callable instance function - function by default is passed 2 arguments,
  the positional argument vector and a map of keyword arguments.  Results are marshalled
  back to python using [[libpython-clj2.python.fn/bridged-fn-arg->python]] which is also
  used when bridging an object into python.  See documentation for
  [[libpython-clj2.python.class/make-kw-instance-fn]]."
  ([ifn options] (py-class/make-kw-instance-fn ifn options))
  ([ifn] (make-kw-instance-fn ifn nil)))



(defn ^:no-doc make-tuple-instance-fn
  [ifn & {:as options}]
  (make-instance-fn ifn options))


(defn create-class
  "Create a new class object.  Any callable values in the cls-hashmap
  will be presented as instance methods.  If you want access to the
  'this' object then you must use `make-instance-fn`.

  Example:

```clojure
user> (require '[libpython-clj2.python :as py])
nil
user> (def cls-obj (py/create-class
                    \"myfancyclass\"
                    nil
                    {\"__init__\" (py/make-instance-fn
                                 (fn [this arg]
                                   (py/set-attr! this \"arg\" arg)
                                   ;;If you don't return nil from __init__ that is an
                                   ;;error.
                                   nil))
                     \"addarg\" (py/make-instance-fn
                               (fn [this otherarg]
                                 (+ (py/get-attr this \"arg\")
                                    otherarg)))}))
#'user/cls-obj
user> cls-obj
__no_module__.myfancyclass
user> (def inst (cls-obj 10))
#'user/inst
user> (py/call-attr inst \"addarg\" 10)
20
```"
  [name bases cls-hashmap]
  (py-class/create-class name bases cls-hashmap))


(defn cfn
  "Call an object.
  Arguments are passed in positionally.  Any keyword
  arguments are paired with the next arg, gathered, and passed into the
  system as *kwargs.

  Not having an argument after a keyword argument is an error."
  [item & args]
  (apply py-fn/cfn item args))


(defn afn
  "Call an attribute of an object.
  Arguments are passed in positionally.  Any keyword
  arguments are paired with the next arg, gathered, and passed into the
  system as *kwargs.

  Not having an argument after a keyword is an error."
  [item attr & args]
  (apply py-fn/afn item attr args))


(defn make-fastcallable
  "Wrap a python callable such that calling it in a tight loop with purely positional
  arguments is a bit (2x-3x) faster.

  Example:

```clojure
user> (def test-fn (-> (py/run-simple-string \"def spread(bid,ask):\n\treturn bid-ask\n\n\")
                       (get :globals)
                       (get \"spread\")))
#'user/test-fn
user> test-fn
<function spread at 0x7f330c046040>
user> (py/with-gil (time (dotimes [iter 10000]
                           (test-fn 1 2))))
\"Elapsed time: 85.140418 msecs\"
nil
user> (py/with-gil (time (dotimes [iter 10000]
                           (test-fn 1 2))))
\"Elapsed time: 70.894275 msecs\"
nil
user> (with-open [test-fn (py/make-fastcallable test-fn)]
        (py/with-gil (time (dotimes [iter 10000]
                             (test-fn 1 2)))))

\"Elapsed time: 39.442622 msecs\"
nil
user> (with-open [test-fn (py/make-fastcallable test-fn)]
        (py/with-gil (time (dotimes [iter 10000]
                             (test-fn 1 2)))))

\"Elapsed time: 35.492965 msecs\"
nil
```"
  ^java.lang.AutoCloseable [item]
  (py-fn/make-fastcallable item))


(defmacro with
  "Support for the 'with' statement in python:
  (py/with [item (py/call-attr testcode-module \"WithObjClass\" true fn-list)]
      (py/call-attr item \"doit_err\"))"
  [bind-vec & body]
  `(py-with/with ~bind-vec ~@body))


(defmacro $a
  "Call an attribute of an object using automatic detection of the python kwargs.
  Keywords must be compile time constants.  So this won't work with 'apply'.  On the
  other hand, building the positional and kw argmaps happens at compile time as
  opposed to at runtime.  The attr name can be a symbol."
  [item attr & args]
  (let [[pos-args kw-args] (py-fn/args->pos-kw-args args)]
    `(call-attr-kw ~item ~(py-fn/key-sym-str->str attr)
                   ~pos-args ~kw-args)))


(defmacro $c
  "Call an object using automatic detection of the python kwargs.
  Keywords must be compile time constants.  So this won't work with 'apply'.  On the
  other hand, building the positional and kw argmaps happens at compile time as
  opposed to at runtime."
  [item & args]
  (let [[pos-args kw-args] (py-fn/args->pos-kw-args args)]
    `(py-fn/call-kw ~item ~pos-args ~kw-args)))


(defmacro py.-
  "Class/object getter syntax.  (py.- obj attr) is equivalent to
  Python's obj.attr syntax."
  [x arg]
  `(get-attr ~x ~(py-fn/key-sym-str->str arg)))


(defmacro py.
  "Class/object method syntax.  (py. obj method arg1 arg2 ... argN)
  is equivalent to Python's obj.method(arg1, arg2, ..., argN) syntax."
  [x method-name & args]
  ;; method-name cast to a string specifically for go and go-loop
  ;; compatability
  `(~#'$a ~x ~(str method-name) ~@args))


(defmacro py*
  "Special syntax for passing along *args and **kwargs style arguments
  to methods.

  Usage:

  (py* obj method args kwargs)

  Example:

  (def d (python/dict))
  d ;;=> {}
  (def iterable [[:a 1] [:b 2]])
  (def kwargs {:cat \"dog\" :name \"taco\"})
  (py* d  update [iterable] kwargs)
  d ;;=> {\"a\": 1, \"b\": 2, \"cat\": \"dog\", \"name\": \"taco\"}"
  ([x method args]
   (list #'call-attr-kw x (py-fn/key-sym-str->str method) args nil))
  ([x method args kwargs]
   (list #'call-attr-kw x (py-fn/key-sym-str->str method) args kwargs)))


(defmacro py**
  "Like py*, but it is assumed that the LAST argument is kwargs."
  ([x method kwargs]
   (list #'call-attr-kw x (str method) nil kwargs))
  ([x method arg & args]
   (let [args   (into [arg] args)
         kwargs (last args)
         args   (vec (pop args))]
     (list #'call-attr-kw x (py-fn/key-sym-str->str method) args kwargs))))


(defn ^:private handle-pydotdot
  ([x form]
   (if (list? form)
     (let [form-data (vec form)
           [instance-member & args] form-data
           symbol-str (str instance-member)]
       (cond
         (clojure.string/starts-with? symbol-str "-")
         (list #'py.- x (symbol (subs symbol-str 1 (count symbol-str))))

         (clojure.string/starts-with? symbol-str "**")
         (list* #'py** x (symbol (subs symbol-str 2 (count symbol-str))) args)

         (clojure.string/starts-with? symbol-str "*")
         (list* #'py* x (symbol (subs symbol-str 1 (count symbol-str))) args)

         :else ;; assumed to be method invocation

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
  sys.path.append('/home/user/bin')

  SPECIAL SYNTAX for programmatic *args and **kwargs

  Special syntax is provided to meet the needs required by
  Python's *args and **kwargs syntax programmatically.


  (= (py.. obj (*method args))
     (py* obj methods args))

  (= (py.. obj (*method args kwargs))
     (py* obj method args kwargs))

  (= (py.. obj (**method kwargs))
     (py** obj method kwargs))

  (= (py.. obj (**method arg1 arg2 arg3 ... argN kwargs))
     (py** obj method arg1 arg2 arg3 ... argN kwargs)
     (py*  obj method [arg1 arg2 arg3 ... argN] kwargs))


  These forms exist for when you need to pass in a map of options
  in the same way you would use the f(*args, **kwargs) forms in
  Python."
  [x & args]
  (apply handle-pydotdot x args))


(defn- module-path-string
  "Given a.b, return a
   Given a.b.c, return a.b
   Given a.b.c.d, return a.b.c  etc."
  [x]
  (clojure.string/join
   "."
   (pop (clojure.string/split (str x) #"[.]"))))


(defn- module-path-last-string
  "Given a.b.c.d, return d"
  [x]
  (last (clojure.string/split (str x) #"[.]")))


(defn path->py-obj
  "Given a string such as \"builtins\" or \"builtins.list\", load the module or
  the class object in the module.

  Options:

  * `:reload` - Reload the module."
  [item-path & {:keys [reload?]}]
  (when (seq item-path)
    (if-let [module-retval (try
                             (import-module item-path)
                             (catch Exception e
                               (when-not (seq (module-path-string item-path))
                                 (throw e))))]
      (if reload?
        (let [import-lib (import-module "importlib")]
          (call-attr import-lib "reload" module-retval))
        module-retval)
      (let [butlast (module-path-string item-path)]
        (if-let [parent-mod (path->py-obj butlast :reload? reload?)]
          (get-attr parent-mod (module-path-last-string item-path))
          (throw (Exception. (format "Failed to find module or class %s"
                                     item-path))))))))


(defmacro def-unpack
  "Unpack a set of symbols into a set of defs.  Useful when trying to match Python
  idioms - this is definitely not idiomatic Clojure.

  Example:

```clojure
user> (py/def-unpack [a b c] (py/->py-tuple [1 2 3]))
#'user/c
user> a
1
user> b
2
user> c
3
```"
  [symbols input]
  `(let [~symbols ~input]
     ~@(for [s symbols] `(def ~s ~s))))
