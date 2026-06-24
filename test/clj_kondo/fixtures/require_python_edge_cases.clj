(ns clj-kondo.fixtures.require-python-edge-cases
  "Edge case tests for require-python clj-kondo hook."
  (:require [libpython-clj2.require :refer [require-python]]))

(require-python '[json :refer :all])

(require-python '[collections :refer :*])

(require-python '[datetime :bind-ns true :reload true :no-arglists true])

(require-python '[urllib.parse :as parse :bind-ns true :refer [urlencode urlparse]])

(defn test-refer-all []
  (datetime/now))

(defn test-combined-refer []
  (urlencode {})
  (urlparse "http://example.com"))
