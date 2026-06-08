(ns libpython-clj2.metadata-test
  (:require [clojure.test :refer :all]
            [libpython-clj2.python :as py]
            [libpython-clj2.metadata :as metadata]))

(deftest pyarglists-preserves-default-values
  (let [argspec {:args ["top" "topdown" "onerror"]
                 :varargs nil
                 :varkw nil
                 :defaults ["." true nil]
                 :kwonlyargs ["follow_symlinks" "dir_fd"]
                 :kwonlydefaults (array-map "follow_symlinks" false
                                            "dir_fd" nil)}]
    (is (= '([& [{top "."
                  topdown true
                  onerror nil
                  follow_symlinks false
                  dir_fd nil}]]
             [& [{top "."
                  topdown true
                  follow_symlinks false
                  dir_fd nil}]]
             [& [{top "."
                  follow_symlinks false
                  dir_fd nil}]]
             [& [{follow_symlinks false
                  dir_fd nil}]])
           (metadata/pyarglists argspec)))))

(deftest py-fn-argspec-stringifies-python-object-defaults
  (let [testcode (py/import-module "testcode")
        default-type-fn (py/get-attr testcode "default_type_fn")]
    (is (= '([& [{dtype "<class 'int'>"}]]
             [])
           (-> default-type-fn
               metadata/py-fn-argspec
               metadata/pyarglists)))))
