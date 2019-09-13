(defproject cnuernber/libpython-clj "1.2"
  :description "libpython bindings to the techascent ecosystem"
  :url "http://github.com/cnuernber/libpython-clj"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [techascent/tech.datatype "4.15"]
                 [camel-snake-kebab "0.4.0"]]
  :repl-options {:init-ns user}
  :java-source-paths ["java"])
