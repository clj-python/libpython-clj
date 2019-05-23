(ns libpython-clj.python
  (:require [libpython-clj.jna :as libpy]
            [libpython-clj.jna.base :as libpy-base]
            [libpython-clj.python.logging
             :refer [log-error log-warn log-info]]
            [tech.parallel.utils :refer [export-symbols]]
            [libpython-clj.python.interop :as pyinterop]
            [libpython-clj.python.interpreter :as pyinterp
             :refer [with-gil with-interpreter]]
            [libpython-clj.python.object :as pyobject])
  (:import [com.sun.jna Pointer]
           [java.io Writer]))


(set! *warn-on-reflection* true)


(defn initialize!
  [& {:keys [program-name no-redirect?]}]
  (when-not @pyinterp/*main-interpreter*
    (pyinterp/initialize! program-name)
    ;;setup bridge mechansim and io redirection
    (when-not no-redirect?)
    )
  :ok)


(defn finalize!
  []
  (pyinterp/finalize!))


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
                python->jvm-copy-iterable
                python->jvm-copy-persistent-vector
                set-attr
                wrap-pyobject)


(export-symbols libpython-clj.python.interop
                run-simple-string
                run-string
                create-function
                create-module
                py-import-module)
