(ns libpython-clj2.python.windows
  (:require [clojure.java.shell :refer [sh]]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(defn create-echo-path-bat! []
  "Creates temporary file to extract condas PATH environment variable"
  (let [tmp (java.io.File/createTempFile "echo-path" ".bat")]
    (spit tmp "echo %PATH%")
    (.toString tmp)))

(defn delete-echo-path-bat! [tmp]
  "Deletes temporary file"
  (io/delete-file tmp))

(defn- get-windows-anaconda-env-path [activate-bat echo-bat]
  "Get anacondas windows PATH environment variable to load native dlls for numpy etc. like python with anaconda does."
  (-> (sh "cmd.exe" "/K" (str activate-bat " & " echo-bat))
      :out
      (s/split #"\r\n")
      reverse
      (nth 2)))


(defn- generate-python-set-env-path [path]
  "Double quote windows path separator \\ -> \\\\"
  (let [quoted (s/replace path "\\" "\\\\")]
    (str
      "import os;\n"
      "path = '" quoted "';\n"
      "os.environ['PATH'] = path;\n")))

(defn setup-windows-conda! [windows-conda-activate-bat
                            run-simple-string]
  "Setup python PATH environment variable like in anaconda to be able to load native dlls for numpy etc. like anaconda does."
  (let [echo-bat (create-echo-path-bat!)]
    (->> (get-windows-anaconda-env-path
           windows-conda-activate-bat
           echo-bat)
         generate-python-set-env-path
         run-simple-string)
    (delete-echo-path-bat! echo-bat)))
