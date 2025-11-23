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
                                 (format "requires-python = \"==%s\"" python-version)]
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
  (let [p (apply process/start process-args)]

    (with-open [in-rdr (java.io.InputStreamReader. (.getInputStream p))
                err-rdr (java.io.InputStreamReader. (.getErrorStream p))]



      (dorun (char-seq in-rdr))
      (dorun (char-seq err-rdr)))))


(defn- uv-installed? []
  (try
    (let [p (process/start  "uv" "--version")]
      ;; We only need to know it starts; stop it quickly.
      (.destroy ^Process p)
      true)
    (catch java.io.IOException _
      false)
    (catch Throwable _
      false)))

(defn sync-python-setup!
  "Synchronize python venv at .venv with 'uv sync'.
  When 'uv' is not available on PATH, throws with guidance to install."
  []
  (println "Synchronize python venv at .venv with 'uv sync'. This might take a few minutes")
  (when-not (uv-installed?)
    (println "The 'uv' tool was not found on your PATH.")
    (println "Install uv from https://github.com/astral-sh/uv, e.g.:")
    (println "  - curl -LsSf https://astral.sh/uv/install.sh | sh")
    (println "  - or: pipx install uv")
    (throw (ex-info "The 'uv' tool is not installed or not on PATH." {:tool "uv" :stage :preflight})))
  (let [deps-edn
        (try
          (-> (slurp "python.edn") edn/read-string)
          (catch java.io.FileNotFoundException e
            (throw (ex-info "Missing python.edn. Copy python.edn.example to python.edn and set :python-version and :python-deps."
                            {:file "python.edn" :stage :read-config} e))))]
    (write-pyproject-toml! deps-edn)
    (start-and-print! ["uv" "sync" "--managed-python" "--python" (-> deps-edn :python-version)])
    (println "Python environment synchronized with uv.")
    true))
 



