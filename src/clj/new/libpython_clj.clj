(ns libpython-clj.libpython-clj
  (:require [clj.new.templates :refer [renderer project-name name-to-path ->files]]))

(def render (renderer "libpython_clj"))

(defn render-it [path-name data]
  (let [pypattern #"\{\{libpython_clj\}\}"
        new-name (clojure.string/replace path-name pypattern  "base")]
    [path-name (render new-name data)]))

(defn file-map->files [data file-map]
  (apply ->files data (seq file-map)))

(defn libpython-clj-template! [name]
  (let [data         {:name      (project-name name)
                      :base      (clojure.string/replace
                                  (project-name name)
                                  #"(.*?)[.](.*$)"
                                  "$1")
                      :sanitized (name-to-path name)}
        {base :base} data]
    (println "Generating libpython-clj template for" (:name data) "at" (:sanitized data))

    (with-bindings {#'clj.new.templates/*dir*        "/tmp/data"
                    #'clj.new.templates/*force?*     true
                    #'clj.new.templates/*overwrite?* true}
      (file-map->files
       data
       {"deps.edn"                 (render "deps.edn" data)
        (format "src/%s.clj" base) (render "core.clj" data)
        "src/python.clj"           (render "python.clj" data)
        "dev/src/build.clj"        (render "dev/src/build.clj" data)
        "dev/src/user.clj"         (render "dev/src/user.clj" data)
        
        }
       

       ;; scripts

       ;; src/cljs/{{base}}
       ))))


(defn libpython-clj [name]
  (libpython-clj-template! name))


