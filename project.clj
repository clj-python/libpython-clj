(defproject clj-python/libpython-clj "2.00-beta-4"
  :description "libpython bindings for Clojure"
  :url "http://github.com/cnuernber/libpython-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure    "1.10.2" :scope "provided"]
                 [camel-snake-kebab      "0.4.0"]
                 [cnuernber/dtype-next   "6.06"]
                 [net.java.dev.jna/jna "5.7.0"]
                 [org.clojure/data.json  "1.0.0"]]
  :profiles {:dev {:dependencies [[criterium "0.4.5"]
                                  [ch.qos.logback/logback-classic "1.1.3"]]}
             :jdk-16 {:jvm-opts ["--add-modules" "jdk.incubator.foreign"
                                 "-Dforeign.restricted=permit"
                                 "-Djava.library.path=/usr/lib/x86_64-linux-gnu"]}
             :codox
             {:dependencies [[codox-theme-rdash "0.1.2"]]
              :plugins [[lein-codox "0.10.7"]]
              :codox {:project {:name "libpython-clj"}
                      :metadata {:doc/format :markdown}
                      :themes [:rdash]
                      :source-paths ["src"]
                      :output-path "docs"
                      :doc-paths ["topics"]
                      :source-uri "https://github.com/clj-python/libpython-clj/blob/master/{filepath}#L{line}"
                      :namespaces [libpython-clj2.python
                                   libpython-clj2.codegen
                                   libpython-clj2.python.np-array
                                   python.builtins
                                   python.numpy]}}}
  :aliases {"codox" ["with-profile" "codox,dev" "codox"]})
