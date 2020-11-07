(ns hooks.libpython-clj.require.import-python
  "The import-python macro from libpython-clj/require.clj"
  (:require [clj-kondo.hooks-api :as api]))

;; from: libpython-clj/require.clj

;; (defn import-python
;;   "Loads python, python.list, python.dict, python.set, python.tuple,
;;   and python.frozenset."
;;   []
;;   (require-python
;;    '(builtins
;;      [list :as python.list]
;;      [dict :as python.dict]
;;      [set :as python.set]
;;      [tuple :as python.tuple]
;;      [frozenset :as python.frozenset]
;;      [str :as python.str])
;;    '[builtins :as python])
;;   :ok)

;; alternative:
;;
;; (require
;;   (quote [builtins.list :as python.list])
;;   (quote [builtins.dict :as python.dict])
;;   (quote [builtins.set :as python.set])
;;   (quote [builtins.tuple :as python.tuple])
;;   (quote [builtins.frozenset :as python.frozenset])
;;   (quote [builtins.str :as python.str])
;;   (quote [builtins :as python]))

(defn make-require
  [ns-sym alias-sym]
  (api/list-node
   [(api/token-node 'require)
    (api/list-node
     [(api/token-node 'quote)
      (api/vector-node
       [(api/token-node ns-sym)
        (api/keyword-node :as)
        (api/token-node alias-sym)])])]))

(defn import-python
  "Macro in libpython-clj/require.clj.

  Example call:

    (import-python)

  May be treating it as:

   (do
     (require (quote [builtins.list :as python.list]))
     (require (quote [builtins.dict :as python.dict]))
     ,,,
     )

  "
  [{:keys [:node]}]
  (let [new-node
        (with-meta (api/list-node
                    [(api/token-node 'do)
                     (make-require 'builtins.list 'python.list)
                     (make-require 'builtins.dict 'python.dict)
                     (make-require 'builtins.set 'python.set)
                     (make-require 'builtins.tuple 'python.tuple)
                     (make-require 'builtins.frozenset 'python.frozenset)
                     (make-require 'builtins.str 'python.str)
                     (make-require 'builtins 'python)])
                   (meta node))]
    ;; XXX: uncomment following and run clj-kondo on cl_format.clj to debug
    ;;(prn (api/sexpr node))
    ;;(prn (api/sexpr new-node))
    {:node new-node}))
