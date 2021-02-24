(ns libpython-clj2.python.with
  "Implementation of the python 'with' keyword"
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.fn :as py-fn]
            [tech.v3.datatype.ffi :as dt-ffi])
  (:import [tech.v3.datatype.ffi Pointer]))


(defn python-pyerr-fetch-error-handler
  "Utility code used in with macro"
  []
  (py-ffi/check-gil)
  (let [ptype (dt-ffi/make-ptr :pointer 0)
        pvalue (dt-ffi/make-ptr :pointer 0)
        ptraceback (dt-ffi/make-ptr :pointer 0)]
    (py-ffi/PyErr_Fetch ptype pvalue ptraceback)
    (py-ffi/PyErr_NormalizeException ptype pvalue ptraceback)
    ;;We own the references so they have to be released.
    (throw (ex-info "python error in flight"
                    {:ptype (Pointer/constructNonZero (ptype 0))
                     :pvalue (Pointer/constructNonZero (pvalue 0))
                     :ptraceback (Pointer/constructNonZero (ptraceback 0))}))))


(defn with-exit-error-handler
  "Utility code used in with macro"
  [with-var error]
  (let [einfo (ex-data error)]
    (if (every? #(contains? einfo %) [:ptype :pvalue :ptraceback])
      (let [{^Pointer ptype :ptype
             ^Pointer pvalue :pvalue
             ^Pointer ptraceback :ptraceback} einfo
            suppress-error? (py-fn/call-attr with-var "__exit__"
                                             [ptype
                                              pvalue
                                              ptraceback])]
        (if (and ptype pvalue ptraceback (not suppress-error?))
          (do
            ;;Manual incref here because we cannot detach the object
            ;;from our gc decref hook added during earlier pyerr-fetch handler.
            (py-ffi/PyErr_Restore ptype pvalue ptraceback)
            (py-ffi/check-error-throw))
          (do
            (when ptype (py-ffi/Py_DecRef ptype))
            (when-not pvalue (py-ffi/Py_DecRef pvalue))
            (when-not ptraceback (py-ffi/Py_DecRef ptraceback)))))
      (do
        (py-fn/call-attr with-var "__exit__" [nil nil nil])
        (throw error)))))


(defmacro with
  "Support for the 'with' statement in python:
  (py/with [item (py/call-attr testcode-module \"WithObjClass\" true fn-list)]
      (py/call-attr item \"doit_err\"))"
  [bind-vec & body]
  (when-not (= 2 (count bind-vec))
    (throw (Exception. "Bind vector must have 2 items")))
  (let [varname (first bind-vec)]
    `(py-ffi/with-gil
       (let [~@bind-vec]
         (with-bindings
           {#'py-ffi/*python-error-handler* python-pyerr-fetch-error-handler}
           (py-fn/call-attr ~varname "__enter__" nil)
           (try
             (let [retval# (do ~@body)]
               (py-fn/call-attr ~varname "__exit__" [nil nil nil])
               retval#)
             (catch Throwable e#
               (with-exit-error-handler ~varname e#))))))))
