(ns libpython-clj.python
  (:require [libpython-clj.jna :as libpy]
            [libpython-clj.jna.base :as libpy-base]
            [libpython-clj.python.logging
             :refer [log-error log-warn log-info]]
            [tech.parallel.utils :refer [export-symbols]]
            [libpython-clj.python.interop :as pyinterop]
            [libpython-clj.python.interpreter :as pyinterp
             :refer [with-gil with-interpreter]]
            [libpython-clj.python.object :as pyobject]
            [libpython-clj.python.bridge])
  (:import [com.sun.jna Pointer]
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
  [& {:keys [program-name no-io-redirect?]}]
  (when-not @pyinterp/*main-interpreter*
    (pyinterp/initialize! program-name)
    ;;setup bridge mechansim and io redirection
    (pyinterop/register-bridge-type!)
    (when-not no-io-redirect?
      (pyinterop/setup-std-writer #'*err* "stderr")
      (pyinterop/setup-std-writer #'*out* "stdout")))
  :ok)


(defn finalize!
  []
  (pyinterp/finalize!))
