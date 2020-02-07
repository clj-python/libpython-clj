(ns {{base}}.python
    (:require [libpython-clj.python :as py]))

(defn initialize-python!
  ([] (py/initialize!))
  ([python-executable]
   (py/initialize! :python-executable python-executable)))

(initialize-python! "python3.7")
