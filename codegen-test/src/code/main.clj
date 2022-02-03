(ns code.main
  (:require [python.numpy :as np]
            [libpython-clj2.python :as py])
  (:gen-class))


(defn setup!
  []
  (py/initialize!))


(defn lspace
  []
  (np/linspace 2 3))


(defn -main
  []
  (setup!)
  (println (lspace))
  (shutdown-agents))
