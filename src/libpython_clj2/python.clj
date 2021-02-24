(ns libpython-clj2.python
  (:require [libpython-clj2.python.info :as py-info]
            [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.fn :as py-fn]
            [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.with :as py-with]
            [libpython-clj2.dechunk-map :refer [dechunk-map]]
            [libpython-clj2.python.copy :as py-copy]
            [libpython-clj2.python.bridge-as-jvm]
            [libpython-clj2.python.bridge-as-python]
            [libpython-clj.python.gc :as pygc]
            [libpython-clj.python.windows :as win]
            [tech.v3.datatype.ffi :as dtype-ffi]
            [clojure.tools.logging :as log]))



(defn initialize!
    "Initialize the python library.  If library path is not provided, then the system
  attempts to execute a simple python program and have python return system info.


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
  [& [{:keys [windows-anaconda-activate-bat
              library-path]} options]]
  (if-not (and (py-ffi/library-loaded?)
                 (= 1 (py-ffi/Py_IsInitialized)))
    (let [info (py-info/detect-startup-info options)
          _ (log/infof "Startup info %s" info)
          libname (->> (concat (when library-path [library-path]) (:libnames info))
                       (dechunk-map identity)
                       (filter #(try
                                  (boolean (dtype-ffi/library-loadable? %))
                                  (catch Throwable e false)))
                       (first))]
      (log/infof "Loading python library: %s" libname)
      (py-ffi/initialize! libname (:python-home info) options)
      (when-not (nil? windows-anaconda-activate-bat)
        (win/setup-windows-conda! windows-anaconda-activate-bat
                                  py-ffi/run-simple-string))
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
  operations as the mutex is captured and held once as opposed to find grained
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
     (pygc/stack-resource-context
      ~@body)))


(defn import-module
  "Import a python module returning an implementation of java.util.Map wrapping
  the module object and consisting of module attributes."
  [modname]
  (with-gil
    (if-let [mod (py-ffi/PyImport_ImportModule modname)]
      (-> (py-ffi/wrap-pyobject mod)
          (py-base/as-jvm))
      (py-ffi/check-error-throw))))


(defn add-module
  "Add a python module.  This can create a module if it doesn't exist."
  [modname]
  (with-gil
    (-> (py-ffi/PyImport_AddModule modname)
        (py-ffi/wrap-pyobject)
        (py-base/as-jvm))))


(defn module-dict
  "Get the module dictionary."
  [mod]
  (with-gil
    (-> (py-ffi/PyModule_GetDict mod)
        (py-ffi/wrap-pyobject)
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
  (with-gil (py-fn/call-attr-kw pyobj attname args kw-list)))

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


(defn get-item
  "Get an item from a python object using  __getitem__"
  [pyobj item-name]
  (with-gil (py-proto/get-item pyobj item-name)))


(defn set-item!
  "Get an item from a python object using  __setitem__"
  [pyobj item-name item-val]
  (with-gil (py-proto/set-item! pyobj item-name item-val))
  pyobj)


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
  [v]
  (py-ffi/with-gil (py-base/->jvm v)))


(defn as-jvm
  "Copy a python value into java datastructures"
  [v]
  (py-ffi/with-gil (py-base/as-jvm v)))


(defn python-type
  "Get the type (as a keyword) of a python object"
  [v]
  (py-ffi/with-gil (py-ffi/pyobject-type-kwd v)))


(defn ->py-list
  "Get the type (as a keyword) of a python object"
  [v]
  (py-ffi/with-gil (py-copy/->py-list v)))


(defn ->py-tuple
  "Get the type (as a keyword) of a python object"
  [v]
  (py-ffi/with-gil (py-copy/->py-tuple v)))


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
    `(call-kw ~item ~pos-args ~kw-args)))


(defmacro py.-
  "Class/object getter syntax.  (py.- obj attr) is equivalent to
  Python's obj.attr syntax."
  [x arg]
  `(get-attr ~x ~(py-fn/key-sym-str->str arg)))


(defmacro py.
  "Class/object method syntax.  (py. obj method arg1 arg2 ... argN)
  is equivalent to Python's obj.method(arg1, arg2, ..., argN) syntax."
  [x & args]
  (list* (into (vector #'$a x) args)))


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
