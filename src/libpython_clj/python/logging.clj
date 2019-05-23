(ns libpython-clj.python.logging
  "Logging api.  Not set on this, potentially slf4j is more appropriate but I don't have
  nice reflective wrappers for it.  timbre has a lot of dependencies and quite a lot of
  them are out of date."
  (:require [tech.parallel.require :as parallel-req]))


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


(defn log-warn
  [log-str]
  (log-level :warn log-str))


(defn log-info
  [log-str]
  (log-level :info log-str))


(defn logthrow-error
  [log-str & [data]]
  (throw (ex-info log-str data)))
