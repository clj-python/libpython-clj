(ns libpython-clj.python.interpreter
  (:require [libpython-clj.jna :as libpy]
            [tech.resource :as resource]
            [libpython-clj.python.logging
             :refer [log-error log-warn log-info]]
            [tech.jna :as jna])
  (:import [libpython_clj.jna
            JVMBridge
            PyObject]
           [com.sun.jna Pointer]
           [java.io StringWriter]))


(set! *warn-on-reflection* true)


(defn get-object-handle
  [obj]
  (System/identityHashCode obj))


;;All interpreters share the same type symbol table as types are uniform
;;across initializations.  So given an unknown item, we can in constant time
;;get the type of that item if we have seen it before.
(defrecord Interpreter [
                        interpreter-state* ;;Thread state, per interpreter
                        shared-state* ;;state shared among all interpreters
                        ])


;;Map of interpreter handle to interpreter
(defonce ^:dynamic *interpreters* (atom {}))


;; Main interpreter booted up during initialize!
(defonce ^:dynamic *main-interpreter* (atom nil))



(defn add-interpreter-handle!
  [interpreter]
  (swap! *interpreters* assoc
         (get-object-handle interpreter)
         interpreter))


(defn remove-interpreter-handle!
  [interpreter]
  (swap! *interpreters* dissoc
         (get-object-handle interpreter)))


(defn handle->interpreter
  [interpreter-handle]
  (if-let [retval (get @*interpreters* interpreter-handle)]
    retval
    (throw (ex-info "Failed to convert from handle to interpreter"
                    {}))))

(defn handle-or-interpreter->interpreter
  [hdl-or-interp]
  (if (number? hdl-or-interp)
    (handle->interpreter hdl-or-interp)
    hdl-or-interp))


;;Bridge objects are generically created objects that can bridge between
;;python and java.  At the very least, they implement JVMBridge
(defn find-jvm-bridge-entry
  ^JVMBridge [handle interpreter]
  (when-let [interpreter (handle-or-interpreter->interpreter interpreter)]
    (when-let [^JVMBridge bridge-object (get-in @(:interpreter-state* interpreter)
                                                [:bridge-objects handle])]
      bridge-object)))


(defn get-jvm-bridge
  ^JVMBridge [handle interpreter]
  (if-let [bridge-obj (find-jvm-bridge-entry handle interpreter)]
    (:jvm-bridge bridge-obj)
    (throw (ex-info "Unable to find bridge for interpreter %s and handle %s"
                    interpreter handle))))


(defn register-bridge!
  [^JVMBridge bridge ^PyObject bridge-pyobject]
  (let [interpreter (.interpreter bridge)
        bridge-handle (get-object-handle (.wrappedObject bridge))]
    (when (find-jvm-bridge bridge-handle (.interpreter bridge))
      (throw (ex-info "already-registered?" {})))
    (swap! (:interpreter-state* interpreter) assoc-in
           [:bridge-objects bridge-handle]
           {:jvm-bridge bridge
            :pyobject bridge-pyobject})
    :ok))


(defn unregister-bridge!
  [^JVMBridge bridge]
  (let [interpreter (.interpreter bridge)
        bridge-handle (get-object-handle (.wrappedObject bridge))]
    (swap! (:interpreter-state* interpreter)
           update :bridge-objects dissoc bridge-handle)
    :ok))


(defn- construct-main-interpreter!
  [thread-state type-symbol-table]
  (when @*main-interpreter*
    (throw (ex-info "Main interpreter is already constructed" {})))
  (let [retval (->Interpreter (atom {:thread-state thread-state
                                     :bridge-objects {}
                                     :sub-interpreters []})
                              ;;This that have to live as long as the main interpreter does
                              (atom {:type-symbol-table type-symbol-table
                                     :forever []}))]
    (reset! *main-interpreter* retval)
    (add-interpreter-handle! retval)
    :ok))


(defn- python-thread-state
  [interpreter]
  (get @(:interpreter-state* interpreter) :thread-state))


(defn release-gil!
  "non-reentrant pathway to release the gil.  It must not be held by this thread."
  [interpreter]
  (let [thread-state (libpy/PyEval_SaveThread)]
    (assoc @(:interpreter-state* interpreter) :thread-state thread-state)))


(defn acquire-gil!
  "Non-reentrant pathway to acquire gil.  It must not be held by this thread."
  [interpreter]
  (libpy/PyEval_RestoreThread (python-thread-state interpreter)))


(defn swap-interpreters!
  "The gil must be held by this thread.  This swaps out the current interpreter
  to make a new one current."
  [old-interp new-interp]
  (libpy/PyThreadState_Swap (python-thread-state old-interp)
                            (python-thread-state new-interp)))




;;Interpreter for current thread that holds the gil
(defonce ^:dynamic *current-thread-interpreter* nil)


(defn ensure-interpreter
  []
  (if-let [retval (or @*main-interpreter*
                      *current-thread-interpreter*)]
    retval
    (throw (ex-info "No interpreters found, perhaps an initialize! call is missing?"
                    {}))))


(defn ensure-bound-interpreter
  []
  (when-not *current-thread-interpreter*
    (throw (ex-info "No interpreter bound to current thread" {})))
  *current-thread-interpreter*)


(defn py-type-keyword
  "Get a keyword that corresponds to the current type.  Uses global type symbol table.
  Add the type to the symbol table if it does not exist already."
  [typeobj]
  (let [type-addr (Pointer/nativeValue (jna/as-ptr typeobj))
        interpreter (ensure-bound-interpreter)
        symbol-table (-> (swap! (:shared-state* interpreter)
                                (fn [{:keys [type-symbol-table] :as shared-state}]
                                  (if-let [found-item (get type-symbol-table type-addr)]
                                    shared-state
                                    (assoc-in shared-state [:type-symbol-table type-addr]
                                              {:typename (libpy/get-type-name typeobj)}))))
                         :type-symbol-table)]
    (get-in symbol-table [type-addr :typename])))


(defn with-gil-fn
  "Run a function with the gil aquired.  If you acquired the gil, release
  it when finished.  Note we also lock the interpreter so that even
  if some code releases thegil, this interpreter cannot be entered."
  ([interpreter body-fn]
   (let [interpreter (or interpreter (ensure-interpreter))]
     (cond
       ;;No interpreters bound
       (not *current-thread-interpreter*)
       (locking interpreter
         (try
           (with-bindings {#'*current-thread-interpreter* interpreter}
             (acquire-gil! interpreter)
             (body-fn))
           (finally
             (release-gil! interpreter))))
       ;;Switch interpreters in the current thread...deadlock
       ;;is possible here.
       (not (identical? interpreter *current-thread-interpreter*))
       (locking interpreter
         (let [old-interp *current-thread-interpreter*]
           (try
             (with-bindings {#'*current-thread-interpreter* interpreter}
               (swap-interpreters! old-interp interpreter)
               (body-fn))
             (finally
               (swap-interpreters! interpreter old-interp)))))
       :else
       (do
         (assert (identical? interpreter *current-thread-interpreter*))
         (body-fn))))))


(defmacro with-gil
  "See with-gil-fn"
  [& body]
  `(with-gil-fn nil (fn [] (do ~@body))))


(defmacro with-interpreter
  "See with-gil-fn"
  [interp & body]
  `(with-gil-fn ~interp (fn [] (do ~@body))))


(defonce ^:dynamic *program-name* "")


(defn initialize!
  [& [program-name]]
  (when-not @*main-interpreter*
    (log-info "executing python initialize!")
    (libpy/Py_InitializeEx 0)
    ;;Set program name
    (when-let [program-name (or program-name *program-name* "")]
      (resource/stack-resource-context
       (libpy/PySys_SetArgv 0 (-> program-name
                                  (jna/string->wide-ptr)))))
    (let [type-symbols (libpy/lookup-type-symbols)]
      (construct-main-interpreter! (libpy/PyEval_SaveThread) type-symbols))))


(defn check-error-str
  "Function assumes python stdout and stderr have been redirected"
  []
  (with-gil
    (when-not (= 0 (libpy/PyErr_Occurred))
      (let [custom-writer (StringWriter.)]
        (with-bindings {#'*err* custom-writer}
          (libpy/PyErr_Print))
        (.toString custom-writer)))))


(defn check-error-throw
  []
  (when-let [error-str (check-error-str)]
    (throw (ex-info error-str {}))))


(defn check-error-log
  []
  (when-let [error-str (check-error-str)]
    (log-error error-str)))


(defn finalize!
  []
  (when *current-thread-interpreter*
    (throw (ex-info "There cannot be an interpreter bound when finalize! is called"
                    {})))
  (check-error-throw)
  (when-let [main-interpreter (first (swap-vals! *main-interpreter* (constantly nil)))]
    (log-info "executing python finalize!")
    (with-bindings {#'*current-thread-interpreter* main-interpreter}
      (acquire-gil! main-interpreter)
      (let [finalize-value (libpy/Py_FinalizeEx)]
        (when-not (= 0 finalize-value)
          (log-error (format "PyFinalize returned nonzero value: %s" finalize-value)))))
    (remove-interpreter-handle! main-interpreter)))


(defn conj-forever!
  [items]
  (let [interpreter (ensure-bound-interpreter)]
    (swap! (:shared-state* interpreter) update :forever conj items)
    :ok))


;;Sub interpreter work goes here
