(ns libpython-clj.classes-test
  (:require [libpython-clj.python :as py]
            [libpython-clj.jna :as pylib]
            [clojure.test :refer :all]
            [clojure.pprint :as pp]
            [clojure.edn :as edn]))



(py/initialize!)

(deftest new-cls-test
  (let [cls-obj (py/create-class
                 "Stock" nil
                 ;;Using as-py-fn instead of ->py-fn to avoid any marshalling.
                 ;;What happens is the self object gets marshalled to a
                 ;;persistent map.  This isn't what we want.
                 {"__init__" (py/make-tuple-fn
                              (fn [self name shares price]
                                (py/set-attr! self "name" name)
                                (py/set-attr! self "shares" shares)
                                (py/set-attr! self "price" price)
                                nil))
                  "cost" (py/make-tuple-fn
                          (fn [self]
                            (* (py/$. self shares)
                               (py/$. self price))))
                  "__str__" (py/make-tuple-fn
                             (fn [self]
                               ;;Self is just a dict so it converts to a hashmap
                               (pr-str {"name" (py/$. self name)
                                        "shares" (py/$. self shares)
                                        "price" (py/$. self price)})))
                  "clsattr" 55})
        new-instance (cls-obj "ACME" 50 90)]
    (is (= 4500
           (py/$a new-instance cost)))
    (is (= 55 (py/$. new-instance clsattr)))

    (is (= {"name" "ACME", "shares" 50, "price" 90}
           (edn/read-string (.toString new-instance))))))
