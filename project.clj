(defproject clj-libpython "0.1-SNAPSHOT"
  :description "libpython bindings to the techascent ecosystem"
  :url "http://github.com/cnuernber/clj-python"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1-beta2"]
                 [techascent/tech.datatype "4.0-alpha29"]]
  :repl-options {:init-ns clj-libpython.core})
