(ns libpython-clj.python
  (:require [libpython-clj.jna :as libpy]
            [tech.jna.base :as jna-base]
            [tech.jna :as jna]
            [tech.resource :as resource]
            [tech.resource.gc :as resource-gc]
            [tech.parallel.require :as parallel-req]
            [clojure.core.async :as async])
  (:import [tech.resource GCSoftReference]
           [com.sun.jna Pointer]
           [com.sun.jna.ptr PointerByReference]
           [java.lang AutoCloseable]))


(set! *warn-on-reflection* true)


(defonce taoensso-logger
  (future {:info  (parallel-req/require-resolve 'tech.jna.timbre-log/log-info)
           :warn (parallel-req/require-resolve 'tech.jna.timbre-log/log-warn)
           :error  (parallel-req/require-resolve 'tech.jna.timbre-log/log-error)
           }))


(defn log-level
  [level msg]
  (if-let [logger (try (get @taoensso-logger level)
                       (catch Throwable e nil))]
    (logger msg)
    (println (format "%s: %s" (name level) msg))))


(defn log-error
  [log-str]
  (log-level :error log-str))


(defn log-info
  [log-str]
  (log-level :info log-str))


(defn logthrow-error
  [log-str & [data]]
  (throw (ex-info log-str data)))


;;All interpreters share the same type symbol table as types are uniform
;;across initializations.  So given an unknown item, we can in constant time
;;get the type of that item if we have seen it before.
(defrecord Interpreter [thread-state* type-symbol-table*])


(defonce ^:dynamic *main-interpreter* (atom nil))
(defonce ^:dynamic *current-thread-interpreter* nil)


(def ^:dynamic *program-name* "")


(defn- finalize-global-interpreter!
  [thread-state-atom]
  (when-let [thread-state (first (swap-vals! thread-state-atom (constantly nil)))]
    (log-info "Destroying global python interpreter")
    (libpy/PyEval_RestoreThread thread-state)
    (let [finalize-val (long (libpy/Py_FinalizeEx))]
      (when-not (= 0 finalize-val)
        (log-error (format "Py_Finalize failure: %s"
                           finalize-val))))))


(defn initialize!
  [& [program-name]]
  (when-not @*main-interpreter*
    (log-info "Creating global python interpreter")
    (libpy/Py_InitializeEx 0)
    ;;Set program name
    (when-let [program-name (or program-name *program-name* "")]
      (resource/stack-resource-context
       (libpy/PySys_SetArgv 0 (-> program-name
                                  (jna/string->wide-ptr)))))
    (let [retval (->Interpreter (atom (libpy/PyEval_SaveThread))
                                (atom (libpy/lookup-type-symbols)))
          thread-state-atom (:thread-state* retval)]
      (reset! *main-interpreter* (resource/track
                                  retval
                                  #(finalize-global-interpreter! thread-state-atom)
                                  [:gc]))
      :ok)))


(defn unsafe-destroy-global-interpreter!
  []
  (when-let [main-interpreter (first (swap-vals! *main-interpreter* (constantly nil)))]
    (finalize-global-interpreter! (:thread-state* main-interpreter))))


(defn- ensure-interpreter
  []
  (if-let [retval (or @*main-interpreter*
                      *current-thread-interpreter*)]
    retval
    (throw (ex-info "No interpreters found" {}))))



(defmacro with-gil
  [interpreter & body]
  `(let [interpreter# (or ~interpreter (ensure-interpreter))
         restore?# (not *current-thread-interpreter*)]
     (if restore?#
       (locking interpreter#
         (try
           (with-bindings {#'*current-thread-interpreter* interpreter#}
             (libpy/PyEval_RestoreThread @(:thread-state* interpreter#))
             ~@body)
           (finally
             (reset! (:thread-state* interpreter#)
                     (libpy/PyEval_SaveThread)))))
       (do
         ~@body))))



(defn wrap-pyobject
  "Wrap object such that when it is no longer accessible via the program decref is
  called."
  [pyobj]
  (let [interpreter (ensure-interpreter)
        pyobj-value (Pointer/nativeValue (jna/as-ptr pyobj))]
    (resource/track pyobj #(with-gil interpreter
                             (libpy/Py_DecRef (Pointer. pyobj-value)))
                    [:gc])))


(defn incref-wrap-pyobject
  "Increment the object's refcount and then call wrap-pyobject."
  [pyobj]
  (with-gil nil
    (libpy/Py_IncRef pyobj)
    (wrap-pyobject pyobj)))


(defn py-true
  []
  (libpy/Py_True))


(defn py-false
  []
  (libpy/Py_False))


(defn py-none
  []
  (libpy/Py_None))


(defn py-not-implemented
  []
  (libpy/Py_NotImplemented))


(defn py-raw-type
  [pyobj]
  (with-gil nil
    (-> (libpy/PyObject_Type pyobj)
        wrap-pyobject)))


(defn py-type-keyword
  [pyobj]
  (with-gil nil
    (let [interpreter (ensure-interpreter)
          sym-table-atom (:type-symbol-table* interpreter)
          sym-table @sym-table-atom
          py-type (py-raw-type pyobj)
          py-type-addr (Pointer/nativeValue ^Pointer py-type)]
      (if-let [retval (get-in sym-table [py-type-addr :typename])]
        retval
        (throw (ex-info "Loading more dynamic types is not yet supported" {}))))))


(defn pyobj->string
  [pyobj]
  (with-gil nil
    (let [temp-obj (-> (libpy/PyObject_Str pyobj)
                       wrap-pyobject)
          str-data (libpy/PyUnicode_AsUTF8 temp-obj)
          retval (jna/variable-byte-ptr->string str-data)]
      retval)))


(defn py-dir
  [pyobj]
  (with-gil nil
    (let [item-dir (libpy/PyObject_Dir pyobj)]
      (->> (range (libpy/PyObject_Length item-dir))
           (mapv (fn [idx]
                   (-> (libpy/PyObject_GetItem item-dir
                                               (libpy/PyLong_FromLong idx))
                       pyobj->string)))))))
