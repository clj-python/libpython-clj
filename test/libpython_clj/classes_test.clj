(ns libpython-clj.classes-test
  (:require [libpython-clj.python :as py]
            [libpython-clj.jna :as pylib]
            [clojure.test :refer :all]
            [clojure.pprint :as pp]
            [clojure.edn :as edn]))


(py/initialize!)


(deftest new-cls-test
  ;;The crux of this is making instance functions.  The make-tuple-instance-fn pathway
  ;;is pretty close to the metal and as such it does no marshalling of parameters by
  ;;default.  You can turn on marshalling of all parameters adding `arg-converter`
  ;;optional argument (this may make your life easier) or you can marshal just the
  ;;parameter you have to (self in the examples below).
  (let [cls-obj (py/create-class
                 "Stock" nil
                 {"__init__" (py/make-tuple-instance-fn
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
                  "cost" (py/make-tuple-instance-fn
                          (fn [self]
                            (* (py/$. self shares)
                               (py/$. self price)))

                          ;;Convert self to something that auto-marshals things.
                          ;;This pathway will autoconvert all arguments to the function.
                          :arg-converter py/as-jvm
                          :method-name "cost")
                  "__str__" (py/make-tuple-instance-fn
                             (fn [self]
                               ;;Alternative to using arg-converter.  This way you can
                               ;;explicitly control which arguments are converted.
                               (let [self (py/as-jvm self)]
                                 (pr-str {"name" (py/$. self name)
                                          "shares" (py/$. self shares)
                                          "price" (py/$. self price)}))))
                  "clsattr" 55})
        new-instance (cls-obj "ACME" 50 90)]
    (is (= 4500
           (py/$a new-instance cost)))
    (is (= 55 (py/$. new-instance clsattr)))
    (is (= {"name" "ACME", "shares" 50, "price" 90}
           (edn/read-string (.toString new-instance))))))
