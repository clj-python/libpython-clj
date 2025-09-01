(ns libpython-clj2.python.uv
  (:require
   [clojure.edn :as edn]
   [clojure.java.process :as process]
   [clojure.string :as str]
   [cheshire.core :as json]
   [clojure.java.process :as proc]))

(defn- write-pyproject-toml! [deps-edn]

  (let [python-deps
        (:python-deps deps-edn)

        python-version (:python-version deps-edn)

        py-project-header-lines ["[project]"
                                 "name = \"temp\""
                                 "version = \"0.0\""
                                 (format "requires-python = \"==%s\"" python-version) 
                                 ]
        python-deps-lines
        (map
         (fn [dep]
           (format "\"%s\"," dep))
         python-deps)

        py-project-lines
        (concat
         py-project-header-lines
         ["dependencies = ["]
         python-deps-lines
         "]\n")]

    (spit "pyproject.toml"
          (str/join "\n" py-project-lines))))

(defn- char-seq
  [^java.io.Reader rdr]
  (let [chr (.read rdr)]
    (when (>= chr 0)
      (do
        ;(def chr chr)
        (print (char (Integer. chr)))
        (flush)
        (cons chr (lazy-seq (char-seq rdr)))))))


(defn- start-and-print! [process-args]
  (let [p(apply process/start process-args)]

    (with-open [in-rdr (java.io.InputStreamReader. (.getInputStream p))
                err-rdr (java.io.InputStreamReader. (.getErrorStream p))]



      (dorun (char-seq in-rdr))
      (dorun (char-seq err-rdr)))))


(defn sync-python-setup! 
  "Synchronize python venv at .venv with 'uv sync'."
  []
  (println "Synchronize python venv at .venv with 'uv sync'. This might take a few minutes")
  (let [deps-edn
        (->
         (slurp "python.edn")
         edn/read-string)]
    (write-pyproject-toml! deps-edn)
    (start-and-print! ["uv" "sync" "--managed-python" "--python" (-> deps-edn :python-version)])))



