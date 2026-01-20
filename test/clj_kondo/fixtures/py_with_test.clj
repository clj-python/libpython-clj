(ns clj-kondo.fixtures.py-with-test
  (:require [libpython-clj2.python :as py]))

(defn test-py-with [testcode]
  (py/with [f (py/call-attr testcode "FileWrapper" "content")]
    (py/call-attr f "read")))
