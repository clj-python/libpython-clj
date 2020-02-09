(defproject clj-python/libpython-clj "1.37-SNAPSHOT"
  :description "libpython bindings to the techascent ecosystem"
  :url "http://github.com/cnuernber/libpython-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[criterium "0.4.5"]]}}
  :java-source-paths ["java"]
  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]})
