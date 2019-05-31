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
                attr
                has-attr?
                set-attr!
                callable?
                has-item?
                item
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
                run-simple-string
                run-string
                libpython-clj-module-name)


(export-symbols libpython-clj.python.bridge
                as-jvm
                as-python)


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


(export-symbols libpython-clj.python.bridge
                as-jvm
                as-python)


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


(comment
  (initialize!)
  (def mm (create-module "mm"))
  (def tt (pyinterop/register-bridge-type! mm))
  (def writer-iface (pyinterop/wrap-var-writer #'*err*))
  (def writer (pyinterop/expose-bridge-to-python! writer-iface mm))
  (pyinterop/setup-std-writer #'*err* mm "stderr")
  (pyinterop/setup-std-writer #'*out* mm "stdout")
  (def sys-module (py-import-module "sys"))
  (def stderr-item (get-attr sys-module "stderr"))
  (def test-fn (get-attr stderr-item "write"))
  )
