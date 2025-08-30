(ns libpython-clj2.python.uv
  (:require
   [clojure.edn :as edn]
   [clojure.java.process :as process]
   [clojure.string :as str]
   [cheshire.core :as json]))

(defn write-pyproject-toml! [deps-edn]

  (let [
        
        python-deps
        (:python-deps deps-edn) 
        
        ;python-version (:python-version deps-edn)
        
        py-project-header-lines ["[project]"
                                 "name = \"temp\""
                                 "version = \"0.0\""
                                 ;(format "requires-python = \"==%s\"" python-version) 
                                  ]
        python-deps-lines
        (map
         (fn [dep]
           (format "\"%s\"," dep))
         python-deps)

        py-project-lines
        (concat
         py-project-header-lines
         [ "dependencies = ["]
         python-deps-lines
         "]\n")
        ]

    (spit "pyproject.toml"
          (str/join "\n" py-project-lines))))

(defn char-seq
  [^java.io.Reader rdr]
  (let [chr (.read rdr)]
    (if (>= chr 0)
      (do
        ;(def chr chr)
        (print (char (Integer. chr)))
        (flush)
        (cons chr (lazy-seq (char-seq rdr)))))))


(defn start-and-print! [process-args]
  (let [args 
        (concat [{:env { "RENV_CONFIG_INSTALL_VERBOSE" "TRUE"}}]
                     process-args)
        p 
        (apply process/start args)]
    
    (with-open [in-rdr (java.io.InputStreamReader. (.getInputStream p))
                err-rdr (java.io.InputStreamReader. (.getErrorStream p))]



      (dorun (char-seq in-rdr))
      (dorun (char-seq err-rdr)))))


(defn setup-python! []
  (println "Synchronize python venv with uv sync. This might take a few minutes")
  (let [deps-edn
        (->
         (slurp "deps.edn")
         edn/read-string)
        
        ]
    (write-pyproject-toml! deps-edn)
    (start-and-print! [ "unbuffer" "uv" "sync" "--python" (-> deps-edn :python-version)]))
  )

(defn setup-r! []
  (let [renv-lock
        (->
         (slurp "deps.edn")
         edn/read-string
         :renv-deps
         (json/generate-string {:pretty true}))]
    (spit "renv.lock" renv-lock))
  
  (start-and-print! ["rm" "-rf" "renv"])
  (start-and-print! ["rm" ".Rprofile"])
  (start-and-print! ["Rscript" "-e" "renv::init()"])
  (start-and-print! ["Rscript" "-e" "renv::restore()"]))

(defn setup! [& args]
  (setup-python!)
  ;(setup-r!)
  )

(comment
  (require 'libpython-clj2.python)
  (require 'libpython-clj2.python.uv)
  (libpython-clj2.python.uv/setup!)
  (libpython-clj2.python/initialize! :python-executable ".venv/bin/python")
  (libpython-clj2.python/run-simple-string "import sys; print(sys.path)")
  (libpython-clj2.python/import-module "openai")
  )