(ns libpython-clj2.codegen-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [libpython-clj2.codegen :as codegen]))

(defn- ns-symbol-for
  [py-mod-or-cls ns-prefix]
  (symbol (str (when-not (s/blank? ns-prefix)
                 (str ns-prefix "."))
               py-mod-or-cls)))

(deftest ns-prefix-nil-test
  (testing "ns-prefix nil should not produce leading dot"
    (is (= 'numpy (ns-symbol-for "numpy" nil)))
    (is (= 'builtins (ns-symbol-for "builtins" nil))))

  (testing "ns-prefix empty string should not produce leading dot"
    (is (= 'numpy (ns-symbol-for "numpy" "")))
    (is (= 'builtins (ns-symbol-for "builtins" ""))))

  (testing "ns-prefix with value should produce prefixed namespace"
    (is (= 'python.numpy (ns-symbol-for "numpy" "python")))
    (is (= 'my.prefix.builtins (ns-symbol-for "builtins" "my.prefix")))))

(deftest write-namespace-nil-prefix-test
  (testing "write-namespace! with nil ns-prefix"
    (let [tmp-dir (str (System/getProperty "java.io.tmpdir") "/libpython-clj-test-" (System/currentTimeMillis))]
      (try
        (codegen/write-namespace! "builtins" {:output-dir tmp-dir
                                              :ns-prefix nil})
        (is (.exists (io/file tmp-dir "builtins.clj")))
        (let [content (slurp (io/file tmp-dir "builtins.clj"))]
          (is (re-find #"\(ns builtins" content)))
        (finally
          (doseq [f (reverse (file-seq (io/file tmp-dir)))]
            (.delete f)))))))
