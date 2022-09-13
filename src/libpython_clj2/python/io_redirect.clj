(ns libpython-clj2.python.io-redirect
  "Implementation of optional io redirection."
  (:require [libpython-clj2.python.class :as py-class]
            [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.bridge-as-python :as py-bridge-py]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.gc :as pygc]
            [libpython-clj2.python.jvm-handle :as jvm-handle]
            [clojure.tools.logging :as log])
  (:import [java.io Writer]))


(defn self->writer
  ^Writer [self]
  (deref (jvm-handle/py-self->jvm-obj self)))

   ;; this works now and prints something

(comment
  (require '[libpython-clj2.python :as py])
  (py/initialize!)
  (def _)
  (def _ (py/run-simple-string "import sys"))
  (py/run-simple-string "for i in range(10):\n        sleep(1)\n        sys.stderr.write('#')\n        sys.stdout.flush()"))



(def writer-cls*
  (jvm-handle/py-global-delay
   (py-class/create-class
    "jvm_io_bridge"
    nil
    {"__init__" (py-class/wrapped-jvm-constructor)
     "__del__" (py-class/wrapped-jvm-destructor)
     "write" (py-class/make-tuple-instance-fn
              (fn [self & args]
                (when (seq args)
                  (.write (self->writer self) (str
                                               (py-base/->jvm
                                                (first args)))))
                (py-ffi/py-none))
              {:arg-converter identity})
     "flush" (py-class/make-tuple-instance-fn
              (fn [self] (.flush *err*))) ; no idea how to pass and use `writer-var` here

              
     "isatty" (py-class/make-tuple-instance-fn
               (constantly (py-ffi/py-false)))})))


(defn setup-std-writer
  [writer-var sys-mod-attname]
  (assert (instance? Writer (deref writer-var)))
  (py-ffi/with-gil
    (pygc/with-stack-context
      (let [sys-module (py-ffi/import-module "sys")
            std-out-writer (@writer-cls* (jvm-handle/make-jvm-object-handle
                                          writer-var))]
        (py-proto/set-attr! sys-module sys-mod-attname std-out-writer)
        :ok))))


(defn redirect-io!
  []
  (setup-std-writer #'*err* "stderr")
  (setup-std-writer #'*out* "stdout"))


(comment
  (require '[libpython-clj2.python :as py])
  (py/initialize!)
  (def _ (py/run-simple-string "from tqdm import tqdm"))
  (def _ (py/run-simple-string "from time import sleep"))
  (def _ (py/run-simple-string "with tqdm(total=100) as pbar:\n    for i in range(10):\n        sleep(1)\n        pbar.update(10)"))


  :ok)
