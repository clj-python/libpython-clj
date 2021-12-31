(ns libpython-clj2.python.ffi
  "Low level bindings to the python shared library system.  Several key pieces of
  functionality are implemented:

  * Declare/implement actual C binding layer
  * Low level library initialization
  * GIL management
  * Error checking
  * High perf tuple, dict creation
  * Addref/decref reference management including tracking objects - binding them
    to the JVM GC
  * python type->clojure keyword table"
  (:require [tech.v3.datatype :as dtype]
            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.ffi.size-t :as ffi-size-t]
            [tech.v3.datatype.struct :as dt-struct]
            [tech.v3.datatype.errors :as errors]
            [tech.v3.datatype.native-buffer :as native-buffer]
            [tech.v3.datatype.nio-buffer :as nio-buffer]
            [tech.v3.datatype.protocols :as dt-proto]
            [tech.v3.resource :as resource]
            [libpython-clj2.python.gc :as pygc]
            [camel-snake-kebab.core :as csk]
            [clojure.tools.logging :as log]
            [clojure.string :as s])
  (:import [java.util.concurrent ConcurrentHashMap]
           [java.util.function Function]
           [tech.v3.datatype.ffi Pointer Library]
           [clojure.lang Keyword Symbol]
           [java.nio.charset StandardCharsets]))


(set! *warn-on-reflection* true)


(declare track-pyobject py-none pystr->str check-error-throw pyobject-type-kwd
         PyGILState_Check)


(def python-library-fns
  {:Py_InitializeEx {:rettype :void
                     :argtypes [['signals :int32]]
                     :requires-gil? false
                     :doc "Initialize the python shared library"}
   :Py_IsInitialized {:rettype :int32
                      :requires-gil? false
                      :doc "Return 1 if library is initalized, 0 otherwise"}

   :PyRun_SimpleString {:rettype :int32
                        :argtypes [['argstr :string]]
                        :doc "Low-level run a simple python string."}
   :PyRun_String {:rettype :pointer
                  :argtypes [['program :string]
                             ['start-sym :int32]
                             ['globals :pointer]
                             ['locals :pointer]]
                  :doc "Run a string setting the start type, globals and locals"}
   :PySys_SetArgvEx {:rettype :void
                     :argtypes [['argc :int32]
                                ['argv-wide-ptr-ptr :pointer]
                                ['update :int32]]
                   :doc "Set the argv/argc for the interpreter.
Required for some python modules"}
   :Py_SetProgramName {:rettype :void
                       :requires-gil? false
                       :argtypes [['program-name-wideptr :pointer]]
                       :doc "Set the program name"}
   :Py_SetPythonHome {:rettype :void
                      :requires-gil? false
                      :argtypes [['python-home-wideptr :pointer]]
                      :doc "Set the program name"}
   :PyEval_SaveThread {:rettype :pointer
                       :requires-gil? false
                       :doc "Release the GIL on the current thread"}

   :PyEval_RestoreThread {:rettype :void
                          :requires-gil? false
                          :argtypes [['threadstate :pointer]]
                          :doc "Restore the python thread state thread"}
   :PyGILState_Ensure {:rettype :int32
                       :requires-gil? false
                       :doc "Ensure this thread owns the python GIL.

Each call must be matched with PyGILState_Release"}
   :PyGILState_Check {:rettype :int32
                      :requires-gil? false
                      :doc "Return 1 if gil is held, 0 otherwise"}
   :PyGILState_Release {:rettype :void
                        :argtypes [['modhdl :int32]]
                        :doc "Release the GIL state."}
   :Py_IncRef {:rettype :void
               :argtypes [['pyobj :pointer]]
               :doc "Increment the reference count on a pyobj"}
   :Py_DecRef {:rettype :void
               :argtypes [['pyobj :pointer]]
               :doc "Decrement the reference count on a pyobj"}
   :PyErr_Occurred {:rettype :pointer
                    :doc "Return the current in-flight exception without clearing it."}
   :PyErr_Fetch {:rettype :void
                 :argtypes [['type :pointer]
                            ['value :pointer]
                            ['tb :pointer]]
                 :doc "Fetch and clear the current exception information"}
   :PyErr_Restore {:rettype :void
                   :argtypes [['type :pointer]
                              ['value :pointer?]
                              ['tb :pointer?]]
                   :doc "Restore the current error state"}
   :PyErr_NormalizeException {:rettype :void
                              :argtypes [['type :pointer]
                                         ['value :pointer]
                                         ['tb :pointer]]
                              :doc "Normalize a python exception."}
   :PyErr_Clear {:rettype :void
                 :doc "Clear the current python error"}
   :PyErr_SetString {:rettype :void
                     :argtypes [['ex-type :pointer]
                                ['data :string]]
                     :doc "Raise an exception with a message"}
   :PyErr_SetNone {:rettype :void
                   :argtypes [['ex-type :pointer]]
                   :doc "Raise an exception with no message"}
   :PyException_SetTraceback {:rettype :int32
                              :argtypes [['val :pointer]
                                         ['tb :pointer]]
                              :doc "Set the traceback on the exception object"}
   :PyUnicode_AsUTF8 {:rettype :pointer
                      :argtypes [['obj :pointer]]
                      :doc "convert a python unicode object to a utf8 encoded string"}
   :PyUnicode_AsUTF8AndSize {:rettype :pointer
                             :argtypes [['obj :pointer]
                                        ['size :pointer]]
                             :doc "Return both the data and the size of the data"}
   :PyImport_ImportModule {:rettype :pointer
                           :argtypes [['modname :string]]
                           :doc "Import a python module"}
   :PyImport_AddModule {:rettype :pointer
                        :argtypes [['modname :string]]
                        :doc "Add a python module"}
   :PyModule_GetDict {:rettype :pointer
                      :argtypes [['module :pointer]]
                      :doc "Get the module dictionary"}
   :PyObject_Dir {:rettype :pointer
                  :argtypes [['pyobj :pointer]]
                  :doc "Get a python sequence of string attribute names"}
   :PyObject_HasAttr {:rettype :int32
                      :argtypes [['o :pointer]
                                 ['attr_name :pointer]]
                      :doc "Return 1 if object has an attribute"}
   :PyObject_HasAttrString {:rettype :int32
                            :argtypes [['o :pointer]
                                       ['attr_name :string]]
                            :doc "Return 1 if object has an attribute"}
   :PyObject_GetAttr {:rettype :pointer
                      :argtypes [['o :pointer]
                                 ['attr_name :pointer]]
                      :doc "get an attribute from an object"}
   :PyObject_GetAttrString {:rettype :pointer
                            :argtypes [['o :pointer]
                                       ['attr_name :string]]
                            :doc "get an attribute from an object"}
   :PyObject_SetAttrString {:rettype :int32
                            :argtypes [['o :pointer]
                                       ['attr_name :string]
                                       ['v :pointer]]}
   :PyObject_SetAttr {:rettype :int32
                      :argtypes [['o :pointer]
                                 ['attr_name :pointer]
                                 ['v :pointer]]
                      :doc "Set an attribute on a python object"}
   :PyObject_GetItem {:rettype :pointer
                      :argtypes [['o :pointer]
                                 ['args :pointer]]
                      :doc "Get an item from a python object"}
   :PyObject_SetItem {:rettype :int32
                      :argtypes [['o :pointer]
                                 ['args :pointer]
                                 ['val :pointer]]
                      :doc "Set an item on a python object"}
   :PyObject_IsTrue {:rettype :int32
                     :argtypes [['o :pointer]]
                     :doc "Check if a python object is true"}
   :PyObject_IsInstance {:rettype :int32
                         :argtypes [['inst :pointer]
                                    ['cls :pointer]]
                         :doc "Check if this object is an instance of this class"}
   :PyObject_Hash {:rettype :size-t
                   :argtypes [['o :pointer]]
                   :doc "Get the hash value of a python object"}
   :PyObject_RichCompareBool {:rettype :int32
                              :argtypes [['lhs :pointer]
                                         ['rhs :pointer]
                                         ['opid :int32]]
                              :doc "Compare two python objects"}
   :PyCallable_Check {:rettype :int32
                      :argtypes [['o :pointer]]
                      :doc "Return 1 if this is a callable object"}
   :PyObject_Call {:rettype :pointer
                   :argtypes [['callable :pointer]
                              ['args :pointer]
                              ['kwargs :pointer?]]
                   :doc "Call a callable object"}
   :PyObject_CallObject {:rettype :pointer
                   :argtypes [['callable :pointer]
                              ['args :pointer?]]
                   :doc "Call a callable object with no kwargs.  args may be nil"}
   :PyMapping_Check {:rettype :int32
                     :argtypes [['pyobj :pointer]]
                     :doc "Check if this object implements the mapping protocol"}
   :PyMapping_Items {:rettype :pointer
                     :argtypes [['pyobj :pointer]]
                     :doc "Get an iterable of tuples of this map."}
   :PySequence_Check {:rettype :int32
                      :argtypes [['pyobj :pointer]]
                      :doc "Check if this object implements the sequence protocol"}
   :PySequence_Length {:rettype :size-t
                       :argtypes [['pyobj :pointer]]
                       :doc "Get the length of a sequence"}
   :PySequence_GetItem {:rettype :pointer
                        :argtypes [['pyobj :pointer]
                                   ['idx :size-t]]
                        :doc "Get a specific item from a sequence"}
   :PyFloat_AsDouble {:rettype :float64
                     :argtypes [['pyobj :pointer]]
                       :doc "Get a double value from a python float"}
   :PyFloat_FromDouble {:rettype :pointer
                       :argtypes [['data :float64]]
                       :doc "Get a pyobject form a long."}

   :PyLong_AsLongLong {:rettype :int64
                       :argtypes [['pyobj :pointer]]
                       :doc "Get the long value from a python integer"}
   :PyLong_AsUnsignedLongLongMask {:rettype :int64
                                   :argtypes [['pyobj :pointer]]
                                   :doc "Get the unsigned long value from a python integer with no overflow checking"}
   :PyLong_FromLongLong {:rettype :pointer
                         :argtypes [['data :int64]]
                         :doc "Get a pyobject form a long."}
   :PyDict_New {:rettype :pointer
                :doc "Create a new dictionary"}
   :PyDict_SetItem {:rettype :int32
                    :argtypes [['dict :pointer]
                               ['k :pointer]
                               ['v :pointer]]
                    :doc "Insert val into the dictionary p with a key of key. key must be hashable; if it isn’t, TypeError will be raised. Return 0 on success or -1 on failure. This function does not steal a reference to val."}
   :PyDict_Next {:rettype :int32
                 :argtypes [['pyobj :pointer]
                            ['ppos :pointer]
                            ['pkey :pointer]
                            ['pvalue :pointer]]
                 :doc "Get the next value from a dictionary"}
   :PyTuple_New {:rettype :pointer
                 :argtypes [['len :size-t]]
                 :doc "Create a new uninitialized tuple"}
   :PyTuple_SetItem {:rettype :int32
                     :argtypes [['tuple :pointer]
                                ['idx :size-t]
                                ['pvalue :pointer]]
                     :doc "Insert a reference to object o at position pos of the tuple pointed to by p. Return 0 on success. If pos is out of bounds, return -1 and set an IndexError exception."}
   :PyTuple_Size {:rettype :size-t
                  :argtypes [['o :pointer]]
                  :doc "return the length of a tuple"}
   :PyTuple_GetItem {:rettype :pointer
                     :argtypes [['o :pointer]
                                ['idx :size-t]]
                     :doc "return a borrowed reference to item at idx"}
   :PyList_New {:rettype :pointer
                :argtypes [['len :size-t]]
                :doc "create a new list"}
   :PyList_SetItem {:rettype :int32
                    :argtypes [['l :pointer]
                               ['idx :size-t]
                               ['v :pointer]]
                    :doc "Set the item at index index in list to item. Return 0 on success. If index is out of bounds, return -1 and set an IndexError exception.  This function steals the reference to v"}
   :PyList_Size {:rettype :size-t
                  :argtypes [['o :pointer]]
                  :doc "return the length of a list"}
   :PyList_GetItem {:rettype :pointer
                     :argtypes [['o :pointer]
                                ['idx :size-t]]
                     :doc "return a borrowed reference to item at idx"}
   :PySet_New {:rettype :pointer
               :argtypes [['items :pointer]]
               :doc "Create a new set"}
   :PyUnicode_FromString {:rettype :pointer
                          :argtypes [['data :string]]
                          :doc "Create a python unicode object from a string"}
   :PyCFunction_NewEx {:rettype :pointer
                       :argtypes [['method-def :pointer]
                                  ['self :pointer?]
                                  ['module :pointer?]]}
   :PyInstanceMethod_New {:rettype :pointer
                          :argtypes [['pyfn :pointer]]
                          :doc "Mark a python function as being an instance method."}
   })


(def python-lib-def* (delay (dt-ffi/define-library
                              python-library-fns
                              ["_Py_NoneStruct"
                               "_Py_FalseStruct"
                               "_Py_TrueStruct"
                               "PyType_Type"
                               "PyExc_StopIteration"
                               "PyRange_Type"
                               "PyExc_Exception"]
                              nil
                              )))
(defonce pyobject-struct-type*
  (delay (dt-struct/define-datatype!
           :pyobject [{:name :ob_refcnt :datatype (ffi-size-t/size-t-type)}
                      {:name :ob_type :datatype (ffi-size-t/size-t-type)}])))

(defn pytype-offset
  ^long []
  (first (dt-struct/offset-of @pyobject-struct-type* :ob_type)))


(defn pyrefcnt-offset
  ^long []
  (first (dt-struct/offset-of @pyobject-struct-type* :ob_refcnt)))


(defn ptr->struct
  [struct-type ptr-type]
  (let [n-bytes (:datatype-size (dt-struct/get-struct-def struct-type))
        src-ptr (dt-ffi/->pointer ptr-type)
        nbuf (native-buffer/wrap-address (.address src-ptr)
                                         n-bytes
                                         src-ptr)]
    (dt-struct/inplace-new-struct struct-type nbuf)))


(defonce ^:private library* (atom nil))
(defonce ^:private library-path* (atom nil))


(defn reset-library!
  []
  (when @library-path*
    (reset! library* (dt-ffi/instantiate-library @python-lib-def*
                                                 (:libpath @library-path*)))))


(defn set-library!
  [libpath]
  (when @library*
    (log/warnf "Python library is being reinitialized to (%s).  Is this what you want?"
               libpath))
  (reset! library-path* {:libpath libpath})
  (reset-library!))

;;Useful for repling around - this regenerates the library function bindings
(reset-library!)


(defn library-loaded? [] (not (nil? @library*)))


(defn current-library
  ^Library []
  @library*)


(def manual-gil (= "true" (System/getProperty "libpython_clj.manual_gil")))


(defmacro check-gil
  "Maybe the most important insurance policy"
  []
  (when-not manual-gil
    `(errors/when-not-error
      (= 1 (PyGILState_Check))
      "GIL is not captured")))


;;When this is true, generated functions will throw an exception if called when the
;;GIL is not captured.  It makes sense to periodically enable this flag in order
;;to ensure we aren't getting away with sneaky non-GIL access to Python.
(def enable-api-gilcheck* (atom false))

(defn enable-gil-check!
  ([] (reset! enable-api-gilcheck* true))
  ([value] (reset! enable-api-gilcheck* (boolean value))))

(defn- find-pylib-fn
  [fn-kwd]
  (let [pylib @library*]
    (errors/when-not-error
     pylib
     "Library not found.  Has set-library! been called?")
    (if-let [retval (fn-kwd @pylib)]
      retval
      (errors/throwf "Python function %s not found" (symbol (name fn-kwd))))))


(defmacro def-py-fn
  [fn-name docs & args]
  (let [fn-kwd (keyword (name fn-name))]
    (errors/when-not-errorf
     (contains? python-library-fns fn-kwd)
     "Python function %s is not defined" fn-name)
    `(defn ~fn-name ~docs
       ~(vec (map first args))
       (let [retval#
             (resource/stack-resource-context
              ((find-pylib-fn ~fn-kwd) ~@(map (fn [[argname marshal-fn]]
                                                `(~marshal-fn ~argname))
                                              args)))]))))


(defmacro define-pylib-functions
  []
  `(do
     ~@(->>
        python-library-fns
        (map
         (fn [[fn-name {:keys [rettype argtypes] :as fn-data}]]
           (let [fn-symbol (symbol (name fn-name))
                 requires-resctx? (first (filter #(= :string %)
                                                 (map second argtypes)))
                 gilcheck? (when @enable-api-gilcheck*
                             (if (contains? fn-data :requires-gil?)
                               (fn-data :requires-gil?)
                               true))]
             `(defn ~fn-symbol
                ~(:doc fn-data "No documentation!")
                ~(mapv first argtypes)
                (let [~'ifn (find-pylib-fn ~fn-name)]
                  (do
                    ~(when gilcheck? `(check-gil))
                    ~(if requires-resctx?
                       `(resource/stack-resource-context
                         (~'ifn ~@(map (fn [[argname argtype]]
                                         (cond
                                           (#{:int8 :int16 :int32 :int64} argtype)
                                           `(long ~argname)
                                           (#{:float32 :float64} argtype)
                                           `(double ~argname)
                                           (= :string argtype)
                                           `(dt-ffi/string->c ~argname)
                                           :else
                                           argname))
                                       argtypes)))
                       `(~'ifn ~@(map (fn [[argname argtype]]
                                        (cond
                                          (#{:int8 :int16 :int32 :int64} argtype)
                                          `(long ~argname)
                                          (#{:float32 :float64} argtype)
                                          `(double ~argname)
                                          (= :string argtype)
                                          `(dt-ffi/string->c ~argname)
                                          :else
                                          argname))
                                      argtypes))))))))))))


(define-pylib-functions)


(defn- deref-ptr-ptr
  ^Pointer [^Pointer val]
  (Pointer. (case (ffi-size-t/offset-t-type)
              :int32 (.getInt (native-buffer/unsafe) (.address val))
              :int64 (.getLong (native-buffer/unsafe) (.address val)))))


(defmacro define-static-symbol
  [symbol-fn symbol-name deref-ptr?]
  (let [sym-delay-name (with-meta (symbol (str symbol-fn "*"))
                         {:doc (format "Dereferences to the value of %s" symbol-name)
                          :private true})]
    `(do
       (def ~sym-delay-name (delay (.findSymbol (current-library) ~symbol-name)))
       ~(if deref-ptr?
          `(defn ~symbol-fn [] (deref-ptr-ptr (deref ~sym-delay-name)))
          `(defn ~symbol-fn [] (deref ~sym-delay-name))))))


(define-static-symbol py-none "_Py_NoneStruct" false)
(define-static-symbol py-true "_Py_TrueStruct" false)
(define-static-symbol py-false "_Py_FalseStruct" false)
(define-static-symbol py-range-type "PyRange_Type" false)
(define-static-symbol py-exc-type "PyExc_Exception" true)
(define-static-symbol py-exc-stopiter-type "PyExc_StopIteration" true)
(define-static-symbol py-type-type "PyType_Type" false)


(defmacro with-decref
  [vardefs & body]
  (let [n-vars (count vardefs)]
    (if (= 2 n-vars)
      `(let ~vardefs
         (try
           ~@body
           (finally
             (when ~(first vardefs)
               (Py_DecRef ~(first vardefs))))))
      `(let [~'obj-data (object-array ~n-vars)]
         (try
           (let [~@(mapcat (fn [[idx [varsym varform]]]
                             [varsym `(let [vardata# ~varform]
                                        (aset ~'obj-data ~idx vardata#)
                                        vardata#)])
                           (map-indexed vector (partition 2 vardefs)))]
             ~@body)
           (finally
             (check-gil)
             (dotimes [idx# ~n-vars]
               (when-let [pyobj#  (aget ~'obj-data idx#)]
                 (Py_DecRef pyobj#)))))))))


(defn incref
  "Increment the refcount returning object.  Legal to call
  on nil, will not incref item and return nil."
  [pyobj]
  (when pyobj (Py_IncRef pyobj))
  pyobj)


(defonce ^{:tag ConcurrentHashMap
           :private true}
  forever-map (ConcurrentHashMap.))


(defn retain-forever
  [item-key item-val]
  (.put forever-map item-key item-val)
  item-val)


(defonce format-exc-pyfn* (atom nil))


(defn- init-exc-formatter
  []
  (check-gil)
  (let [tback-mod (PyImport_ImportModule "traceback")
        format-fn (PyObject_GetAttrString tback-mod "format_exception")]
    ;;the tback module cannot be removed from memory and it always has a reference
    ;;to format-fn so we drop our reference.
    (Py_DecRef tback-mod)
    (Py_DecRef format-fn)
    (reset! format-exc-pyfn* format-fn)))


(defn- exc-formatter
  []
  (when-not @format-exc-pyfn*
    (init-exc-formatter))
  @format-exc-pyfn*)


(defn append-java-library-path!
  [new-search-path]
  (let [existing-paths (-> (System/getProperty "java.library.path")
                           (s/split #":"))]
    (when-not (contains? (set existing-paths) new-search-path)
      (let [new-path-str (s/join ":" (concat [new-search-path]
                                             existing-paths))]
        (System/setProperty "java.library.path" new-path-str)))))


(defn initialize!
  [libpath python-home & [{:keys [signals? program-name python-home]
                           :or {signals? true
                                program-name ""}
                           :as opts}]]
  (set-library! libpath)
  (when-not (= 1 (Py_IsInitialized))
    (log/debug "Initializing Python C Layer")
    ;;platform specific encoding
    (let [encoding-options {:encoding :wchar-t}
          program-name (retain-forever :program-name
                                       (-> (or program-name "")
                                           (dt-ffi/string->c encoding-options)))]
      (Py_SetProgramName program-name)
      (when python-home
        (let [python-home (retain-forever :python-home
                                          (dt-ffi/string->c python-home
                                                            encoding-options))]
          (log/debugf "Python Home: %s" (:python-home opts))
          (Py_SetPythonHome python-home)))
      (Py_InitializeEx (if signals? 1 0))
      (PySys_SetArgvEx 0 program-name 1)
      ;;return value ignored :-)
      ;;This releases the GIL until further processing and allows with-gil to work
      ;;correctly.
      (PyEval_SaveThread)))
  :ok)


(defn untracked->python
  ^Pointer [item & [conversion-fallback]]
  (cond
    (instance? Pointer item)
    (-> (if (.isNil ^Pointer item)
          (py-none)
          item)
        (incref))
    (instance? Number item) (if (integer? item)
                              (PyLong_FromLongLong (long item))
                              (PyFloat_FromDouble (double item)))
    (instance? Boolean item) (-> (if item (py-true) (py-false))
                                 (incref))
    (instance? String item) (PyUnicode_FromString item)
    (instance? Keyword item) (PyUnicode_FromString (name item))
    (instance? Symbol item) (PyUnicode_FromString (name item))
    (nil? item) (incref (py-none))
    :else
    (if conversion-fallback
      (incref (conversion-fallback item))
      (throw (Exception. "Unable to convert value %s" item)))))


(defn untracked-tuple
  "Low-level make tuple fn.  conv-fallback is used when an argument isn't an
  atomically convertible python type.  Returns an untracked python tuple."
  [args & [conv-fallback]]
  (check-gil)
  (let [args (vec args)
        argcount (count args)
        tuple (PyTuple_New argcount)]
    (dotimes [idx argcount]
      (PyTuple_SetItem tuple idx (untracked->python (args idx)
                                                    conv-fallback)))
    tuple))


(defn untracked-dict
  "Low-level make dict fn.  conv-fallback is used when a key or value isn't an
  atomically convertible python type. Returns an untracked dict."
  [item-seq & [conv-fallback]]
  (check-gil)
  (let [dict (PyDict_New)]
    (pygc/with-stack-context
      (doseq [[k v] item-seq]
        ;;setitem does not steal the reference
        (let [k (untracked->python k conv-fallback)
              v (untracked->python v conv-fallback)
              si-retval (PyDict_SetItem dict k v)]
          (Py_DecRef k)
          (Py_DecRef v)
          (when-not (== (long si-retval) 0)
            (check-error-throw)))))
    dict))

(def ^:dynamic *python-error-handler* nil)


(defn fetch-normalize-exception
  []
  (check-gil)
  (resource/stack-resource-context
   (let [type (dt-ffi/make-ptr :pointer 0)
         value (dt-ffi/make-ptr :pointer 0)
         tb (dt-ffi/make-ptr :pointer 0)]
     (PyErr_Fetch type value tb)
     (PyErr_NormalizeException type value tb)
     {:type (Pointer/constructNonZero (type 0))
      :value (Pointer/constructNonZero (value 0))
      :traceback (Pointer/constructNonZero (tb 0))})))


(defn check-error-str
  "Function assumes python stdout and stderr have been redirected"
  []
  (check-gil)
  (when-not (= nil (PyErr_Occurred))
    (if *python-error-handler*
      (*python-error-handler*)
      (let [{:keys [type value traceback]}
            (fetch-normalize-exception)]
        (with-decref [argtuple (untracked-tuple [type value traceback])
                      exc-str-tuple (PyObject_CallObject (exc-formatter) argtuple)]
          (case (pyobject-type-kwd exc-str-tuple)
            :list (->> (range (PyList_Size exc-str-tuple))
                       (map (fn [idx]
                              (let [strdata (PyList_GetItem exc-str-tuple idx)]
                                (pystr->str strdata))))
                       (s/join))
            :tuple (->> (range (PyTuple_Size exc-str-tuple))
                        (map (fn [idx]
                               (let [strdata (PyTuple_GetItem exc-str-tuple idx)]
                                 (pystr->str strdata))))
                       (s/join))))))))

(defn check-error-throw
  []
  (when-let [error-str (check-error-str)]
    (throw (Exception. ^String error-str))))


(defmacro with-error-check
  [& body]
  `(let [retval# (do ~@body)]
     (check-error-throw)
     retval#))


(defn check-py-method-return
  [^long retval]
  (when-not (= 0 retval)
    (check-error-throw)))


(defn ^:no-doc lock-gil
  ^long []
  (if-not (== 1 (unchecked-long (PyGILState_Check)))
    (PyGILState_Ensure)
    (Long/MIN_VALUE)))


(defn ^:no-doc unlock-gil
  [^long gilstate]
  (when-not (== Long/MIN_VALUE gilstate)
    (when (== 1 gilstate)
      (pygc/clear-reference-queue))
    (PyGILState_Release gilstate)))


(defmacro with-gil
  "Grab the gil and use the main interpreter using reentrant acquire-gil pathway."
  [& body]
  (if manual-gil
    `(let [retval# (do ~@body)]
       (check-error-throw)
       retval#)
    `(let [gil-state# (when-not (== 1 (unchecked-long (PyGILState_Check)))
                        (PyGILState_Ensure))]
       (try
         (let [retval# (do ~@body)]
           (check-error-throw)
           retval#)
         (finally
           (when gil-state#
             #_(System/gc)
             (pygc/clear-reference-queue)
             (PyGILState_Release gil-state#)))))))


(defn pyobject-type
  ^Pointer [pobj]
  (if (= :int32 (ffi-size-t/size-t-type))
    (Pointer. (.getInt (native-buffer/unsafe)
                       (+ (.address (dt-ffi/->pointer pobj)) (pytype-offset))))
    (Pointer. (.getLong (native-buffer/unsafe)
                        (+ (.address (dt-ffi/->pointer pobj)) (pytype-offset))))))


(defn pyobject-refcount
  ^long [pobj]
  (if (= :int32 (ffi-size-t/size-t-type))
    (.getInt (native-buffer/unsafe)
             (+ (.address (dt-ffi/->pointer pobj)) (pyrefcnt-offset)))
    (.getLong (native-buffer/unsafe)
              (+ (.address (dt-ffi/->pointer pobj)) (pyrefcnt-offset)))))


(defn pystr->str
  ^String [pyobj]
  ;;manually allocate/deallocate for speed; this gets called a lot
  (let [size-obj (dt-ffi/make-ptr :pointer 0 {:resource-type nil
                                              :uninitialized? true})
        ^Pointer str-ptr (PyUnicode_AsUTF8AndSize pyobj size-obj)
        nbuf (native-buffer/wrap-address (.address str-ptr)
                                         (size-obj 0)
                                         nil)]
    (native-buffer/free size-obj)
    (-> (.decode StandardCharsets/UTF_8
                 ;;avoid resource chaining for performance
                 ^java.nio.ByteBuffer (nio-buffer/native-buf->nio-buf
                                       nbuf {:resource-type nil}))
        (.toString))))

(defn pytype-name
  ^String [type-pyobj]
  (with-gil
    (if-let [obj-name (PyObject_GetAttrString type-pyobj "__name__")]
      (pystr->str obj-name)
      (do
        (log/warn "Failed to get typename for object")
        "failed-typename-lookup"))))


(defonce ^{:tag ConcurrentHashMap} type-addr->typename-kwd (ConcurrentHashMap.))


(defn pyobject-type-kwd
  [pyobject]
  (let [pytype (pyobject-type pyobject)]
    (.computeIfAbsent type-addr->typename-kwd
                      (.address pytype)
                      (reify Function
                        (apply [this type-addr]
                          (-> (pytype-name pytype)
                              (csk/->kebab-case-keyword)))))))



(def object-reference-logging (atom nil))


(defn- wrap-obj-ptr
  "This must be called with the GIL captured"
  [pyobj ^Pointer pyobjptr gc-data]
  (let [addr (.address pyobjptr)]
    (when @object-reference-logging
      (log/infof "tracking object  - 0x%x:%4d:%s"
                 addr
                 (pyobject-refcount pyobj)
                 (name (pyobject-type-kwd pyobjptr))
                 #_(with-out-str
                     (try
                       (throw (Exception. "test"))
                       ""
                       (catch Exception e
                         (clojure.stacktrace/print-stack-trace e))))))
    (pygc/track pyobj
                ;;we know the GIL is captured in this method
                #(try
                   ;;Intentionally overshadow pyobj.  We cannot access it here.
                   (let [pyobjptr (Pointer. addr)
                         ;;reference gc data
                         gc-data (identity gc-data)]
                     (let [refcount (pyobject-refcount pyobjptr)
                           typename (pyobject-type-kwd pyobjptr)]
                       (if (< refcount 1)
                         (log/errorf "Fatal error -- releasing object - 0x%x:%4d:%s
Object's refcount is bad.  Crash is imminent"
                                     addr
                                     refcount
                                     typename)
                         (when @object-reference-logging
                           (log/infof (format "releasing object - 0x%x:%4d:%s"
                                              addr
                                              refcount
                                              typename)))))
                     (Py_DecRef pyobjptr))
                   (catch Throwable e
                     (log/error e "Exception while releasing object"))))))


(defn track-pyobject
  ^Pointer [pyobj & [{:keys [skip-error-check?
                             gc-data]}]]
  (check-gil)
  (when-let [^Pointer pyobjptr (when pyobj (dt-ffi/->pointer pyobj))]
    (if-not (= (py-none) pyobjptr)
      (wrap-obj-ptr pyobj pyobjptr gc-data)
      ;;Py_None is handled separately
      (do
        (Py_DecRef pyobjptr)
        (when-not skip-error-check? (check-error-throw))
        nil))))


(defn incref-track-pyobject
  ^Pointer [pyobj]
  (when pyobj
    (Py_IncRef pyobj)
    (track-pyobject pyobj)))



(def start-symbol-table
  {:py-single-input 256
   :py-file-input 257
   :py-eval-input 258})


(defn start-symbol
  [item]
  (let [value (cond
               (number? item)
               (long item)
               (keyword? item)
               (get start-symbol-table item 0))
        valid-values (set (vals start-symbol-table))]
    (when-not (contains? valid-values value)
      (throw (ex-info (format "%s is not a start symbol" item) {})))
    (int value)))


(defn import-module
  [modname]
  (if-let [mod (PyImport_ImportModule modname)]
    (track-pyobject mod)
    (check-error-throw)))


(defn run-simple-string
  "Run a simple string.  Results are only visible if they are saved in the
  global or local context.

  https://mail.python.org/pipermail/python-list/1999-April/018011.html

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
  (with-gil
    ;;borrowed reference
    (let [main-mod (PyImport_AddModule "__main__")
          ;;another borrowed reference that we will expose to the user
          globals (or globals (incref-track-pyobject (PyModule_GetDict main-mod)))
          locals (or locals globals)
          retval (track-pyobject
                  (PyRun_String (str program)
                                (start-symbol :py-file-input)
                                globals locals))]
      {:globals globals
       :locals locals
       :retval retval})))


(defn simplify-or-track
  "If input is an atomic python object (long, float, string, bool), return
  the JVM equivalent and release the reference to pyobj.  Else track the object."
  [^Pointer pyobj]
  (if-not (or (nil? pyobj) (.isNil pyobj))
    (if (= pyobj (py-none))
      (do (Py_DecRef pyobj)
          nil)
      (case (pyobject-type-kwd pyobj)
        :int (with-decref [pyobj pyobj]
               (PyLong_AsUnsignedLongLongMask pyobj))
        :float (with-decref [pyobj pyobj]
                 (PyFloat_AsDouble pyobj))
        :str (with-decref [pyobj pyobj]
               (pystr->str pyobj))
        :bool (with-decref [pyobj pyobj]
                (== 1 (long (PyObject_IsTrue pyobj))))
        ;;maybe copy, maybe bridge - in any case we have to decref the item
        (track-pyobject pyobj)))
    (check-error-throw)))
