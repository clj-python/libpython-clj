(ns {{base}}.core
    (:require [libpython-clj.python :as py :refer [py. py.- py.. py* py**]]
              {{base}}.python
              [libpython-clj.require :refer [require-python import-python]]))

(import-python)

(comment
  (require-python 'os)
  (os/getcwd))


