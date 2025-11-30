(ns clj-kondo.hook-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(def ^:private config-dir
  "resources/clj-kondo.exports/clj-python/libpython-clj")

(def ^:private fixtures-dir
  "test/clj_kondo/fixtures")

(defn- run-clj-kondo [file]
  (sh "clj-kondo" "--lint" file "--config-dir" config-dir))

(defn- has-require-python-errors? [output]
  (boolean (re-find #"(Unknown require option|:bind-ns|:reload|:no-arglists)" output)))

(defn- has-unresolved-refer-errors? [output]
  (boolean (re-find #"Unresolved symbol: (webpush|secure_filename|urlencode|urlparse)" output)))

(deftest require-python-hook-test
  (testing "require_python_test.clj - basic require-python usage"
    (let [{:keys [out err]} (run-clj-kondo (str fixtures-dir "/require_python_test.clj"))
          output (str out err)]
      (is (not (has-require-python-errors? output))
          (str "Found require-python errors in output:\n" output))
      (is (not (has-unresolved-refer-errors? output))
          (str "Found unresolved symbol errors for referred symbols:\n" output)))))

(deftest require-python-edge-cases-test
  (testing "require_python_edge_cases.clj - edge cases and variations"
    (let [{:keys [out err]} (run-clj-kondo (str fixtures-dir "/require_python_edge_cases.clj"))
          output (str out err)]
      (is (not (has-require-python-errors? output))
          (str "Found require-python errors in output:\n" output))
      (is (not (has-unresolved-refer-errors? output))
          (str "Found unresolved symbol errors for referred symbols:\n" output)))))
