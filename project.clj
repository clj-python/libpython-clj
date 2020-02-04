(defproject cnuernber/libpython-clj "1.37-SNAPSHOT"
  :description "libpython bindings to the techascent ecosystem"
  :url "http://github.com/cnuernber/libpython-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [camel-snake-kebab "0.4.0"]
                 [techascent/tech.datatype "4.74"]
                 [org.clojure/data.json "0.2.7"]]
  :profiles {:dev {:dependencies [[criterium "0.4.5"]]}}
  :java-source-paths ["java"])
