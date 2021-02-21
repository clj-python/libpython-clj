(ns libpython-clj2.python.pyjvm-fn
  "Pathways for creating clojure functions from python callable objects and
  vice versa.  This namespace expects the GIL is captured."
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.base :as py-base]
            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.struct :as dt-struct]
            [clojure.tools.logging :as log]
            [clojure.stacktrace :as st]))


(def methoddef-type (dt-struct/define-datatype! :pymethodef
                      [{:name :ml_name :datatype (dt-ffi/size-t-type)}
                       {:name :ml_meth :datatype (dt-ffi/size-t-type)}
                       {:name :ml_flags :datatype :int32}
                       {:name :ml_doc :datatype (dt-ffi/size-t-type)}]))


(def tuple-fn-iface (dt-ffi/define-foreign-interface :pointer [:pointer :pointer]))


(def ^{:tag 'long} METH_VARARGS  0x0001)
(def ^{:tag 'long} METH_KEYWORDS 0x0002)
;; METH_NOARGS and METH_O must not be combined with the flags above.
(def ^{:tag 'long} METH_NOARGS   0x0004)


(defn clj-fn->py-callable
  ([ifn {:keys [arg-converter
                result-converter
                name doc]
         :or {arg-converter py-base/->jvm
              result-converter (py-base/->python-incref)
              name "_nnamed"
              doc "no documentation provided"}}]
   (let [fn-ptr
         (dt-ffi/instantiate-foreign-interface
          tuple-fn-iface
          (fn [self tuple-args]
            (try
              (let [retval
                    (apply ifn
                           (->> (range (py-ffi/PyTuple_Size tuple-args))
                                (map (fn [idx]
                                       (-> (py-ffi/PyTuple_GetItem tuple-args idx)
                                           (arg-converter))))))]
                (if result-converter
                  (result-converter retval)
                  retval))
              (catch Throwable e
                (log/error e "Error executing clojure function.")
                (py-ffi/PyErr_SetString
                 (py-ffi/py-exc-type)
                 (format "%s:%s" e (with-out-str
                                     (st/print-stack-trace e))))))))
         ;;no resource tracking - we leak the struct
         method-def (dt-struct/new-struct :pymethoddef {:resource-type nil})
         name (dt-ffi/string->c name)
         doc (dt-ffi/string->c name)]
     (.put method-def :ml_name (.address (dt-ffi/->pointer name)))
     (.put method-def :ml_meth (.address (dt-ffi/->pointer fn-ptr)))
     (.put method-def :ml_flags METH_VARARGS)
     (.put method-def :ml_doc (.address (dt-ffi/->pointer doc)))
     ;;the method def cannot ever go out of scope
     (py-ffi/retain-forever (gensym) {:md method-def
                                      :name name
                                      :doc doc})
     ;;no self, no module reference
     (-> (py-ffi/PyCFunction_NewEx method-def nil nil)
         (py-ffi/wrap-pyobject))))
  ([ifn]
   (clj-fn->py-callable ifn nil)))
