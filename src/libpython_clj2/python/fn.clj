(ns libpython-clj2.python.fn
  "Pathways for creating clojure functions from python callable objects and
  vice versa.  This namespace expects the GIL is captured."
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.copy :as py-copy]
            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.struct :as dt-struct]
            [clojure.tools.logging :as log]
            [clojure.stacktrace :as st]))

(set! *warn-on-reflection* true)


(def methoddef-type (dt-struct/define-datatype! :pymethoddef
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
              result-converter py-base/->python-incref
              name "_unamed"
              doc "no documentation provided"}}]
   (let [fn-inst
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
         fn-ptr (dt-ffi/foreign-interface-instance->c tuple-fn-iface fn-inst)
         ;;no resource tracking - we leak the struct
         method-def (dt-struct/new-struct :pymethoddef {:resource-type nil
                                                        :container-type :native-heap})
         name (dt-ffi/string->c name {:resource-type nil})
         doc (dt-ffi/string->c name {:resource-type nil})]
     (.put method-def :ml_name (.address (dt-ffi/->pointer name)))
     (.put method-def :ml_meth (.address (dt-ffi/->pointer fn-ptr)))
     (.put method-def :ml_flags METH_VARARGS)
     (.put method-def :ml_doc (.address (dt-ffi/->pointer doc)))
     ;;the method def cannot ever go out of scope
     (py-ffi/retain-forever (gensym) {:md method-def
                                      :name name
                                      :doc doc
                                      :fn-ptr fn-ptr
                                      :fn-inst fn-inst})
     ;;no self, no module reference
     (-> (py-ffi/PyCFunction_NewEx method-def nil nil)
         (py-ffi/wrap-pyobject))))
  ([ifn]
   (clj-fn->py-callable ifn nil)))


(defn call-py-fn
  [callable arglist kw-arg-map]
  (py-ffi/with-gil
    (-> (cond
          (seq kw-arg-map)
          (py-ffi/PyObject_Call callable
                                (py-copy/->py-tuple arglist)
                                (py-copy/->py-dict kw-arg-map))
          (seq arglist)
          (py-ffi/PyObject_CallObject callable (py-copy/->py-tuple arglist))
          :else
          (py-ffi/PyObject_CallObject callable nil))
        (py-ffi/wrap-pyobject))))


(defn call
  "Call a python function with positional args.  For keyword args, see call-kw."
  [callable & args]
  (call-py-fn callable args nil))


(defn call-kw
  "Call a python function with a vector of positional args and a map of keyword args."
  [callable arglist kw-args]
  (call-py-fn callable arglist kw-args))


(defn call-attr
  "Call an object attribute with positional arguments."
  [item att-name & args]
  (-> (py-proto/get-attr item att-name)
      (call-py-fn args nil)))


(defn call-attr-kw
  "Call an object attribute with a vector of positional args and a
  map of keyword args."
  [item att-name arglist kw-map]
  (-> (py-proto/get-attr item att-name)
      (call-py-fn arglist kw-map)))

(defn args->pos-kw-args
  "Utility function that, given a list of arguments, separates them
  into positional and keyword arguments.  Throws an exception if the
  keyword argument is not followed by any more arguments."
  [arglist]
  (loop [args arglist
         pos-args []
         kw-args nil
         found-kw? false]
    (if-not (seq args)
      [pos-args kw-args]
      (let [arg (first args)
            [pos-args kw-args args found-kw?]
            (if (keyword? arg)
              (if-not (seq (rest args))
                (throw (Exception.
                        (format "Keyword arguments must be followed by another arg: %s"
                                (str arglist))))
                [pos-args (assoc kw-args arg (first (rest args)))
                 (drop 2 args) true])
              (if found-kw?
                (throw (Exception.
                        (format "Positional arguments are not allowed after keyword arguments: %s"
                                arglist)))
                [(conj pos-args (first args))
                 kw-args
                 (rest args) found-kw?]))]
        (recur args pos-args kw-args found-kw?)))))


(defn cfn
  "Call an object.
  Arguments are passed in positionally.  Any keyword
  arguments are paired with the next arg, gathered, and passed into the
  system as *kwargs.

  Not having an argument after a keyword argument is an error."
  [item & args]
  (let [[pos-args kw-args] (args->pos-kw-args args)]
    (call-kw item pos-args kw-args)))


(defn key-sym-str->str
  [attr-name]
  (cond
    (or (keyword? attr-name)
        (symbol? attr-name))
    (name attr-name)
    (string? attr-name)
    attr-name
    :else
    (throw (Exception.
            "Only keywords, symbols, or strings can be used to access attributes."))))


(defn afn
  "Call an attribute of an object.
  Arguments are passed in positionally.  Any keyword
  arguments are paired with the next arg, gathered, and passed into the
  system as *kwargs.

  Not having an argument after a keyword argument is an error."
  [item attr & args]
  (let [[pos-args kw-args] (args->pos-kw-args args)]
    (call-attr-kw item (key-sym-str->str attr)
                  pos-args kw-args)))
