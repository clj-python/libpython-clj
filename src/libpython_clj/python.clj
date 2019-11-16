(ns libpython-clj.python
  (:require [tech.parallel.utils :refer [export-symbols]]
            [libpython-clj.python.interop :as pyinterop]
            [libpython-clj.python.interpreter :as pyinterp
             :refer [with-gil with-interpreter]]
            [libpython-clj.python.object :as pyobj]
            [libpython-clj.python.bridge]
            [libpython-clj.jna :as pyjna]
            [tech.jna :as jna]
            [libpython-clj.jna.concrete.err :as py-err])
  (:import [com.sun.jna Pointer]
           [com.sun.jna.ptr PointerByReference]
           [java.io Writer]
           [libpython_clj.jna PyObject]))


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
                ->py-dict
                ->py-float
                ->py-list
                ->py-long
                ->py-string
                ->py-tuple
                ->py-fn
                ->python
                ->jvm)


(export-symbols libpython-clj.python.interop
                libpython-clj-module-name)


(export-symbols libpython-clj.python.bridge
                as-jvm
                as-python
                ->numpy
                as-numpy)


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
  [& {:keys [program-name no-io-redirect? library-path]}]
  (when library-path
    (alter-var-root #'libpython-clj.jna.base/*python-library*
                    (constantly library-path)))
  (when-not @pyinterp/*main-interpreter*
    (pyinterp/initialize! program-name)
    ;;setup bridge mechansim and io redirection
    (pyinterop/register-bridge-type!)
    (when-not no-io-redirect?
      (pyinterop/setup-std-writer #'*err* "stderr")
      (pyinterop/setup-std-writer #'*out* "stdout")))
  :ok)


(defn finalize!
  "Finalize the interpreter.  You probably shouldn't call this as it destroys the
  global interpreter and reinitialization is unsupported cpython."
  []
  (pyinterp/finalize!))


(defmacro with
  "Support for the 'with' statement in python."
  [bind-vec & body]
  (when-not (= 2 (count bind-vec))
    (throw (Exception. "Bind vector must have 2 items")))
  (let [varname (first bind-vec)]
    `(with-gil
       (let [~@bind-vec]
         (try
           (with-bindings
           {#'libpython-clj.python.interpreter/*python-error-handler*
            (fn []
              (let [ptype# (PointerByReference.)
                    pvalue# (PointerByReference.)
                    ptraceback# (PointerByReference.)
                    _# (pyjna/PyErr_Fetch ptype# pvalue# ptraceback#)
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
                                 :ptraceback ptraceback#}))))}
             (call-attr ~varname "__enter__")
             (let [retval#
                   (do
                     ~@body)]
               (call-attr ~varname "__exit__" nil nil nil)
               retval#))
           (catch Throwable e#
             (let [einfo# (ex-data e#)]
               (if (= #{:ptype :pvalue :ptraceback} (set (keys einfo#)))
                 (let [{ptype# :ptype
                        pvalue# :pvalue
                        ptraceback# :ptraceback} einfo#
                       suppress-error?# (call-attr ~varname "__exit__"
                                                   ptype#
                                                   pvalue#
                                                   ptraceback#)]
                   (when (and ptype# pvalue# ptraceback#
                              (not suppress-error?#))
                     (do
                       ;;MAnuall incref here because we cannot detach the object
                       ;;from our gc decref hook added above.
                       (pyjna/Py_IncRef ptype#)
                       (pyjna/Py_IncRef pvalue#)
                       (pyjna/Py_IncRef ptraceback#)
                       (pyjna/PyErr_Restore ptype# pvalue# ptraceback#)
                       (pyinterp/check-error-throw))))
                 (throw e#)))))))))
