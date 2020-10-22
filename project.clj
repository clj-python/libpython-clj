(defproject clj-python/libpython-clj "2.00-alpha-2-SNAPSHOT"
  :description "libpython bindings for Clojure"
  :url "http://github.com/cnuernber/libpython-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure    "1.10.2-alpha2"]
                 [camel-snake-kebab      "0.4.0"]
                 [cnuernber/dtype-next   "6.00-alpha-9"]
                 [techascent/tech.jna    "3.24"]
                 [org.clojure/data.json  "1.0.0"]]
  :profiles {:dev {:dependencies [[criterium "0.4.5"]]}
             :codox
             {:dependencies [[codox-theme-rdash "0.1.2"]]
              :plugins [[lein-codox "0.10.7"]]
              :codox {:project {:name "tech.ml.dataset"}
                      :metadata {:doc/format :markdown}
                      :themes [:rdash]
                      :source-paths ["src"]
                      :output-path "docs"
                      :doc-paths ["topics"]
                      :source-uri "https://github.com/clj-python/libpython-clj/blob/master/{filepath}#L{line}"
                      :namespaces [libpython-clj.python]}}}
  :java-source-paths ["java"]
  :aliases {"codox" ["with-profile" "codox,dev" "codox"]}

  )
