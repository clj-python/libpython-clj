(ns libpython-clj2.metadata-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
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

(deftest py-fn-argspec-stringifies-kwonly-python-object-defaults
  (let [testcode (py/import-module "testcode")
        kw-default-type-fn (py/get-attr testcode "kw_default_type_fn")]
    (is (= '([& [{dtype "<class 'int'>"}]])
           (-> kw-default-type-fn
               metadata/py-fn-argspec
               metadata/pyarglists)))))

(defn- tc [n] (py/get-attr (py/import-module "testcode") n))

(defn- default-of [n sym]
  (->> (-> (tc n) metadata/py-fn-argspec metadata/pyarglists first)
       (tree-seq coll? seq) (filter map?) first sym))

(deftest py-default-class-object
  (is (= "<class 'int'>" (default-of "f_class" 'x))))

(deftest py-default-bad-repr-preserves-var
  (is (= '([& [{x "<unprintable>"}]] [])
         (-> (tc "f_badstr") metadata/py-fn-argspec metadata/pyarglists))))

(deftest py-default-custom-repr
  (is (= (apply str (repeat 40 "x")) (default-of "f_weird" 'x))))

(deftest py-default-partial
  (is (= "functools.partial(<class 'int'>, 0)" (default-of "f_partial" 'x))))

(deftest py-default-nested-opaque-no-pointer-leak
  (is (= "(<class 'int'>, <class 'str'>)" (default-of "f_nested_opaque" 'x))))

(deftest py-default-lambda
  (is (str/starts-with? (default-of "f_lambda" 'x) "<function <lambda> at 0x")))

(deftest py-default-sentinel
  (is (str/starts-with? (default-of "f_sentinel" 'x) "<object object at 0x")))

(deftest py-default-huge-truncated
  (let [s (default-of "f_huge" 'model)]
    (is (<= (count s) 203))
    (is (str/ends-with? s "..."))))

(deftest py-default-huge-kwonly-truncated
  (let [s (default-of "f_kw_huge" 'model)]
    (is (<= (count s) 203))
    (is (str/ends-with? s "..."))))

(deftest py-default-mixed
  (let [m (->> (-> (tc "f_mixed") metadata/py-fn-argspec metadata/pyarglists first)
               (tree-seq coll? seq) (filter map?) first)]
    (is (= 1 (m 'b)))
    (is (= "<class 'int'>" (m 'c)))
    (is (str/starts-with? (m 'd) "<object object at 0x"))
    (is (= 2 (m 'e)))))
