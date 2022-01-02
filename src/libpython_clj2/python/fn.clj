(ns libpython-clj2.python.fn
  "Pathways for creating clojure functions from python callable objects and
  vice versa.  This namespace expects the GIL is captured.
  Functions bridging this way is relatively expensive but it is the foundation
  that the more advanced bridging in class.clj is built upon.

  Also contains mechanisms for calling python functions and attributes."
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.gc :as pygc]
            [libpython-clj2.python.copy :as py-copy]
            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.ffi.size-t :as ffi-size-t]
            [tech.v3.datatype.struct :as dt-struct]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.protocols :as dt-proto]
            [clojure.tools.logging :as log]
            [clojure.stacktrace :as st])
  (:import [tech.v3.datatype.ffi Pointer]
           [java.util Map Set]
           [libpython_clj2.python.protocols PBridgeToPython]
           [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)


(def methoddef-type (dt-struct/define-datatype! :pymethoddef
                      [{:name :ml_name :datatype (ffi-size-t/ptr-t-type)}
                       {:name :ml_meth :datatype (ffi-size-t/ptr-t-type)}
                       {:name :ml_flags :datatype :int32}
                       {:name :ml_doc :datatype (ffi-size-t/ptr-t-type)}]))


(def tuple-fn-iface*
  (delay (dt-ffi/define-foreign-interface :pointer? [:pointer :pointer])))
(def kw-fn-iface*
  (delay (dt-ffi/define-foreign-interface :pointer? [:pointer :pointer :pointer])))


(def ^{:tag 'long} METH_VARARGS  0x0001)
(def ^{:tag 'long} METH_KEYWORDS 0x0002)
;; METH_NOARGS and METH_O must not be combined with the flags above.
(def ^{:tag 'long} METH_NOARGS   0x0004)


(defn- internal-make-py-c-fn
  [ifn fn-iface raw-arg-converter meth-type
   {:keys [name doc result-converter]
    :or {name "_unamed"
         doc "no documentation provided"}}]
  (py-ffi/with-gil
    (let [fn-inst
          (dt-ffi/instantiate-foreign-interface
           fn-iface
           (fn [self tuple-args & [kw-args]]
             (try
               (let [retval (apply ifn (raw-arg-converter tuple-args kw-args))]
                 (if result-converter
                   (py-ffi/untracked->python retval result-converter)
                   retval))
               (catch Throwable e
                 (log/error e "Error executing clojure function.")
                 (py-ffi/PyErr_SetString
                  (py-ffi/py-exc-type)
                  (format "%s:%s" e (with-out-str
                                      (st/print-stack-trace e))))))))
          fn-ptr (dt-ffi/foreign-interface-instance->c fn-iface fn-inst)
          ;;no resource tracking - we leak the struct
          method-def (dt-struct/new-struct :pymethoddef {:resource-type nil
                                                         :container-type :native-heap})
          name (dt-ffi/string->c name {:resource-type nil})
          doc (dt-ffi/string->c doc {:resource-type nil})]
      (.put method-def :ml_name (.address (dt-ffi/->pointer name)))
      (.put method-def :ml_meth (.address (dt-ffi/->pointer fn-ptr)))
      (.put method-def :ml_flags meth-type)
      (.put method-def :ml_doc (.address (dt-ffi/->pointer doc)))
      ;;the method def cannot ever go out of scope
      (py-ffi/retain-forever (gensym) {:md method-def
                                       :name name
                                       :doc doc
                                       :fn-ptr fn-ptr
                                       :fn-inst fn-inst})
      ;;no self, no module reference
      (-> (py-ffi/PyCFunction_NewEx method-def nil nil)
          (py-ffi/track-pyobject)))))


(defn raw-tuple-arg-converter
  [arg-converter tuple-args kw-args]
  ;;no kw arguments
  (->> (range (py-ffi/PyTuple_Size tuple-args))
       (mapv (fn [idx]
               (-> (py-ffi/PyTuple_GetItem tuple-args idx)
                   (arg-converter))))))


(defn bridged-fn-arg->python
  "Slightly clever so we can pass ranges and such as function arguments."
  ([item opts]
   (cond
     (instance? PBridgeToPython item)
     (py-proto/as-python item opts)
     (dt-proto/convertible-to-range? item)
     (py-copy/->py-range item)
     (dtype/reader? item)
     (py-proto/->python (dtype/->reader item) opts)
     ;;There is one more case here for iterables that aren't anything else -
     ;; - specifically for sequences.
     (and (instance? Iterable item)
          (not (instance? Map item))
          (not (instance? String item))
          (not (instance? Set item)))
     (py-proto/as-python item opts)
     :else
     (py-base/->python item opts)))
  ([item]
   (bridged-fn-arg->python item nil)))


(defn convert-kw-args
  [{:keys [arg-converter] :as options} tuple-args kw-args]
  [(raw-tuple-arg-converter arg-converter tuple-args nil)
   (->> (py-proto/as-jvm kw-args options)
        (into {}))])


(defn make-tuple-fn
  ([ifn {:keys [arg-converter
                result-converter
                name doc]
         :or {arg-converter py-base/->jvm
              result-converter py-base/->python
              name "_unamed"
              doc "no documentation provided"}
         :as options}]
   (let [arg-converter (or arg-converter identity)
         ;;apply defaults to options map.
         options (assoc options
                        :arg-converter arg-converter
                        :result-converter result-converter
                        :name name
                        :doc doc)]
     (internal-make-py-c-fn ifn @tuple-fn-iface*
                            #(raw-tuple-arg-converter arg-converter %1 %2)
                            METH_VARARGS
                            options)))
  ([ifn]
   (make-tuple-fn ifn nil)))


(defn make-kw-fn
  ([ifn {:keys [arg-converter
                result-converter
                name doc
                kw-arg-converter]
         :or {arg-converter py-base/->jvm
              result-converter py-base/->python
              name "_unamed"
              doc "no documentation provided"}
         :as options}]
   (let [arg-converter (or arg-converter :identity)
         options (assoc options
                        :arg-converter arg-converter
                        :result-converter result-converter
                        :name name
                        :doc doc)
         kw-arg-converter (or kw-arg-converter #(convert-kw-args options %1 %2))]
     (internal-make-py-c-fn ifn @kw-fn-iface*
                            kw-arg-converter
                            (bit-or METH_VARARGS METH_KEYWORDS)
                            options)))
  ([ifn]
   (make-kw-fn ifn nil)))


(defn call-py-fn
  [callable arglist kw-arg-map arg-converter]
  (py-ffi/with-gil
    ;;Release objects marshalled just for this call immediately
    (let [retval
          (pygc/with-stack-context
            (py-ffi/with-decref
              ;;We go out of our way to avoid tracking the arglist because it is
              ;;allocated/deallocated so often
              [arglist (when (or (seq kw-arg-map) (seq arglist))
                         (py-ffi/untracked-tuple arglist arg-converter))
               kw-arg-map (when (seq kw-arg-map)
                            (py-ffi/untracked-dict kw-arg-map arg-converter))]
              (cond
                kw-arg-map
                (py-ffi/PyObject_Call callable arglist kw-arg-map)
                arglist
                (py-ffi/PyObject_CallObject callable arglist)
                :else
                (py-ffi/PyObject_CallObject callable nil))))]
      (py-ffi/simplify-or-track retval))))


(extend-type Pointer
  py-proto/PyCall
  (call [callable arglist kw-arg-map]
    (call-py-fn callable arglist kw-arg-map py-base/->python))
  (marshal-return [callable retval]
    retval))


(defn call
  "Call a python function with positional args.  For keyword args, see call-kw."
  [callable & args]
  (py-proto/call callable args nil))


(defn call-kw
  "Call a python function with a vector of positional args and a map of keyword args."
  [callable arglist kw-args]
  (py-proto/call callable arglist kw-args))


(defn call-attr-kw
  "Call an object attribute with a vector of positional args and a
  map of keyword args."
  [item att-name arglist kw-map arg-converter]
  (py-ffi/with-gil
    (if (string? att-name)
      (py-ffi/with-decref [attval (py-ffi/PyObject_GetAttrString item att-name)]
        (when-not attval (py-ffi/check-error-throw))
        (->> (call-py-fn attval arglist kw-map arg-converter)
             (py-proto/marshal-return item)))
      (py-ffi/with-decref
        [att-name (py-ffi/untracked->python att-name py-base/->python)
         att-val (py-ffi/untracked->python att-name py-base/->python)]
        (when (or (nil? att-name) (nil? att-val))
          (py-ffi/check-error-throw))
        (->> (call-py-fn att-val arglist kw-map arg-converter)
             (py-proto/marshal-return item))))))

(defn call-attr
  "Call an object attribute with only positional arguments."
  [item att-name arglist]
  (call-attr-kw item att-name arglist nil py-base/->python))

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


(def ^{:tag 'long} max-fastcall-args 10)


(defn allocate-fastcall-context
  ^objects []
  (object-array 1))


(defn release-fastcall-context
  [call-ctx]
  (when call-ctx
    (when-let [arglist (aget ^objects call-ctx 0)]
      (py-ffi/Py_DecRef arglist)
      (aset ^objects call-ctx 0 nil))))


(defmacro implement-fastcall
  []
  `(defn ~'fastcall
     "Call a python function as fast as possible reusing the argument tuple.  This function
  takes an object array of length 1 for the call context cache.

  Use allocate-fastcall-context and release-fastcall-context in order to manage the
  context's lifetime.  Do not use same context with fastcall invokations of differing
  arities."
     ([~'item]
      (py-ffi/with-gil
        (-> (py-ffi/PyObject_CallObject ~'item nil)
            (py-ffi/simplify-or-track))))
     ~@(->> (range 1 (inc max-fastcall-args))
            (map
             (fn [n-args]
               (let [arity-args (map (comp symbol #(str "arg-" %)) (range n-args))
                     argdef (->> (concat [(with-meta 'call-ctx
                                            {:tag 'objects})
                                          'item] arity-args)
                                 (vec))]
                 `(~argdef
                   (py-ffi/with-gil
                     (let [~'arglist (if-let [call-tuple# (aget ^objects ~'call-ctx 0)]
                                       call-tuple#
                                       (let [new-t# (py-ffi/PyTuple_New ~n-args)]
                                         (aset ^objects ~'call-ctx 0 new-t#)))
                           ~@(mapcat (fn [argsym]
                                       [argsym `(py-ffi/untracked->python ~argsym)])
                                     arity-args)]
                       ~@(map-indexed (fn [idx argsym]
                                        `(py-ffi/PyTuple_SetItem ~'arglist ~idx ~argsym))
                                      arity-args)
                       (-> (py-ffi/PyObject_CallObject ~'item ~'arglist)
                           (py-ffi/simplify-or-track)))))))))))

(implement-fastcall)


(defmacro reify-fastcallable
  [item]
  `(let [~'ctx-list (object-array max-fastcall-args)]
     (reify
       AutoCloseable
       (close [this#]
         (py-ffi/with-gil
           (dotimes [idx# (alength ~'ctx-list)]
             (release-fastcall-context (aget ~'ctx-list idx#))
             (aset ~'ctx-list idx# nil))))
       clojure.lang.IFn
       (invoke [this#] (fastcall ~item))
       ~@(->> (range 1 (inc max-fastcall-args))
           (map
            (fn [argc]
              (let [arglist (mapv #(symbol (str "arg-" %)) (range argc))
                    ctx-idx (dec argc)]
                `(invoke [this# ~@arglist]
                         (let [~'ctx (if-let [ctx# (aget ~'ctx-list ~ctx-idx)]
                                       ctx#
                                       (let [ctx# (allocate-fastcall-context)]
                                         (aset ~'ctx-list ~ctx-idx ctx#)
                                         ctx#))]
                           (fastcall ~'ctx ~item ~@arglist)))))))

       (applyTo [this# ~'argseq]
         (let [~'argseq (vec ~'argseq)
               ~'n-args (count ~'argseq)]
           (when (> ~'n-args max-fastcall-args)
             (throw (Exception. (format "Maximum fastcall arguments is %d - %d provided"
                                        max-fastcall-args ~'n-args))))
           (if (== 0 ~'n-args)
             (fastcall ~item)
             (let [~'ctx-idx (dec ~'n-args)
                   ~'ctx (if-let [ctx# (aget ~'ctx-list ~'ctx-idx)]
                           ctx#
                           (let [ctx# (allocate-fastcall-context)]
                             (aset ~'ctx-list ~'ctx-idx ctx#)
                             ctx#))]
               (case ~'n-args
                 ~@(->> (range 1 max-fastcall-args)
                        (mapcat
                         (fn [argc]
                           [argc `(fastcall ~'ctx ~item
                                            ~@(->> (range argc)
                                                   (map (fn [idx]
                                                          `(~'argseq ~idx)))))])))))))))))


(defn make-fastcallable
  "Make an auto-disposable fastcallable object that will override the IFn interface and
  always use the fastcall pathways.  This object *must* be closed and thus should be used
  in a with-open scenario but there is no need to specifically allocate fastcall context
  objects.

  See [[fastcall]] for more information."
  ^AutoCloseable [item]
  (py-ffi/with-gil
    (when-not (= 1 (py-ffi/PyCallable_Check item))
      (throw (Exception. "Item passed in does not appear to be a callable python object")))
    (reify-fastcallable item)))


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
                  pos-args kw-args py-base/->python)))
