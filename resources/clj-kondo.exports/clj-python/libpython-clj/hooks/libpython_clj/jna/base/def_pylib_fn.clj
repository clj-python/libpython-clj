;; XXX: work on export-symbols from tech.parallel.utils?
(ns hooks.libpython-clj.jna.base.def-pylib-fn
  "The def-pylib-fn macro from libpython-clj/jna/base.clj"
  (:require [clj-kondo.hooks-api :as api]))

;; from: libpython-clj/jna/base.clj

;; (defmacro def-pylib-fn
;;   [fn-name docstring rettype & argpairs]
;;   `(defn ~fn-name
;;      ~docstring
;;      ~(mapv first argpairs)
;;      (when-not (== (current-thread-id) (.get ^AtomicLong gil-thread-id))
;;        (throw (Exception. "Failure to capture gil when calling into libpython")))
;;      (let [~'tvm-fn (jna/find-function ~(str fn-name) *python-library*)
;;            ~'fn-args (object-array
;;                       ~(mapv (fn [[arg-symbol arg-coersion]]
;;                                (when (= arg-symbol arg-coersion)
;;                                  (throw (ex-info (format "Argument symbol (%s) cannot match coersion (%s)"
;;                                                          arg-symbol arg-coersion)
;;                                                  {})))
;;                                `(~arg-coersion ~arg-symbol))
;;                              argpairs))]
;;        ~(if rettype
;;           `(.invoke (jna-base/to-typed-fn ~'tvm-fn) ~rettype ~'fn-args)
;;           `(.invoke (jna-base/to-typed-fn ~'tvm-fn) ~'fn-args)))))

;; called like:

;; (def-pylib-fn PyImport_AddModule
;;  "Return value: Borrowed reference.
;;
;;   Similar to PyImport_AddModuleObject(), but the name is a UTF-8 encoded string instead
;;   of a Unicode object."
;;  Pointer
;;  [name str])

(defn def-pylib-fn
  "Macro in libpython-clj/jna/base.clj.

  Example call:

    (def-pylib-fn PyImport_AddModule
      \"Return value: Borrowed reference.

       Similar to PyImport_AddModuleObject(), but the name is a UTF-8 ...
       of a Unicode object.\"
      Pointer
      [name str])

  This has the form:

    (def-pylib-fn fn-name docstring rettype & argpairs)

  May be treating it as:

    (defn fn-name
      [arg1 arg2 ,,, argn]
      [arg1 arg2 ,,, argn]) ; fake usage of args?

  where arg1, ..., argn are extracted from argpairs is acceptable.

  XXX: using the second elements of argpairs might be worth doing
       to avoid unused warnings.

  "
  [{:keys [:node]}]
  (let [[_ fn-name _ _ & argpairs] (:children node)
        pairs (map (fn [vec-node]
                     (let [[an-arg a-type] (:children vec-node)]
                       [an-arg a-type]))
                   argpairs)
        new-node (with-meta (api/list-node
                               (apply concat
                                      [(api/token-node 'defn)
                                       fn-name
                                       (api/vector-node (map first pairs))]
                                      pairs))
                     (meta node))]
    ;; XXX: uncomment following and run clj-kondo on cl_format.clj to debug
    ;;(prn (api/sexpr node))
    ;;(prn (api/sexpr new-node))
    {:node new-node}))
