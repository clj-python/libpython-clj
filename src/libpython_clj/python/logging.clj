(ns libpython-clj.python.logging
  "Wrapper for simple logging.  Use tools.logging ns directly for more
  advanced usages."
  (:require [clojure.tools.logging :as log]))


(set! *warn-on-reflection* true)


(defn log-level
  [level msg]
  (case level
    :info (log/info msg)
    :warn (log/warn msg)
    :error (log/error msg)))


(defn log-error
  [log-str]
  (log-level :error log-str))


(defn log-warn
  [log-str]
  (log-level :warn log-str))


(defn log-info
  [log-str]
  (log-level :info log-str))


(defn logthrow-error
  [log-str & [data]]
  (throw (ex-info log-str data)))
