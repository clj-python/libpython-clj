(ns clj-kondo.fixtures.py-dot-test
  (:require [libpython-clj2.python :as py :refer [py. py.. py.-]]))

(defn test-py-macros [obj serialization padding hashes Fernet rsa]
  (py.. Fernet generate_key decode)
  (py.. serialization -Encoding -PEM)
  (py.. obj (private_bytes
             :encoding (py.. serialization -Encoding -PEM)
             :format (py.. serialization -PrivateFormat -PKCS8))
        decode)
  (py.. padding (OAEP
                 :mgf (py.. padding (MGF1 :algorithm (py.. hashes SHA256)))
                 :algorithm (py.. hashes SHA1)
                 :label nil))
  (py.. rsa (generate_private_key :public_exponent 65537 :key_size 2048))
  (py. obj method arg1 arg2)
  (py.- obj some_attribute))
