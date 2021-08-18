(ns libpython-clj2.classes-test
  (:require [libpython-clj2.python :as py]
            [clojure.test :refer :all]
            [clojure.edn :as edn]))


(py/initialize!)


(deftest new-cls-test
  ;;The crux of this is making instance functions to get the 'self' parameter
  ;;passed in.
  (let [cls-obj (py/create-class
                 "Stock" nil
                 {"__init__" (py/make-instance-fn
                              (fn [self name shares price]
                                ;;Because we did not use an arg-converter, all the
                                ;;arguments above are raw jna Pointers - borrowed
                                ;;references.
                                (py/set-attrs! self {"name" name
                                                     "shares" shares
                                                     "price" price})
                                ;;If you don't return nil from __init__ that is an
                                ;;error.
                                nil))
                  "cost" (py/make-instance-fn
                          (fn [self]
                            (* (py/py.- self shares)
                               (py/py.- self price)))
                          {:name "cost"})
                  "__str__" (py/make-instance-fn
                             (fn [self]
                               (pr-str {"name" (py/py.- self name)
                                        "shares" (py/py.- self shares)
                                        "price" (py/py.- self price)})))
                  "kw_clj_fn" (py/make-kw-instance-fn
                               (fn [pos-args kw-args]
                                 (let [self (first pos-args)
                                       price (double (py/py.- self price))]
                                   ;;keywords become strings!!
                                   (apply + price (kw-args "a")
                                          (drop 1 pos-args)))))
                  "clsattr" 55})
        new-instance (cls-obj "ACME" 50 90)]
    (is (= 4500
           (py/$a new-instance cost)))
    (is (= 55 (py/py.- new-instance clsattr)))
    (is (= {"name" "ACME", "shares" 50, "price" 90}
           (edn/read-string (.toString new-instance))))
    (is (= 116.0 (py/call-attr-kw new-instance "kw_clj_fn" [1 2 3] {:a 20})))))
