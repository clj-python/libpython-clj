(ns clj-kondo.fixtures.py-dot-test
  (:require [libpython-clj2.python :as py :refer [py. py.. py.-]]))

(defn test-py-macros [obj serialization padding hashes Fernet rsa]
  ;; py.. chained method calls
  (py.. Fernet generate_key decode)

  ;; py.. with attribute access using - prefix
  (py.. serialization -Encoding -PEM)

  ;; py.. method call with keyword args then chained
  (py.. obj (private_bytes
             :encoding (py.. serialization -Encoding -PEM)
             :format (py.. serialization -PrivateFormat -PKCS8))
        decode)

  ;; py.. with nested method calls
  (py.. padding (OAEP
                 :mgf (py.. padding (MGF1 :algorithm (py.. hashes SHA256)))
                 :algorithm (py.. hashes SHA1)
                 :label nil))

  ;; py.. generate and chain
  (py.. rsa (generate_private_key :public_exponent 65537 :key_size 2048))

  ;; py. single method call
  (py. obj method arg1 arg2)

  ;; py.- attribute access
  (py.- obj some_attribute))
