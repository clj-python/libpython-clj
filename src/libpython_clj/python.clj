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


(export-symbols libpython-clj.python.object
                ->py-dict
                ->py-float
                ->py-list
                ->py-long
                ->py-string
                ->py-tuple
                ->pyobject
                copy-to-jvm
                copy-to-python
                get-attr
                has-attr?
                incref-wrap-pyobject
                obj-get-item
                obj-has-item?
                obj-set-item
                py-dir
                py-false
                py-none
                py-not-implemented
                py-raw-type
                py-string->string
                py-true
                py-type-keyword
                pyobj->string
                python->jvm-copy-hashmap
                python->jvm-iterable
                python->jvm-copy-persistent-vector
                set-attr
                wrap-pyobject)


(export-symbols libpython-clj.python.interop
                import-module
                add-module
                run-simple-string
                run-string
                create-function
                libpython-clj-module-name)

(export-symbols libpython-clj.python.bridge
                ;; python->jvm
                ;; jvm->python
                )


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
