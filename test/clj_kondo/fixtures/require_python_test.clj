(ns clj-kondo.fixtures.require-python-test
  "Test fixtures for require-python clj-kondo hook.
   This file should produce ZERO errors when linted with the hook."
  (:require [libpython-clj2.require :refer [require-python import-python]]))

(import-python)

(require-python '[numpy :as np]
                '[pandas :as pd]
                '[matplotlib.pyplot :as plt])

(require-python '[pathlib :bind-ns true])

(require-python '[requests :reload true])

(require-python '[sklearn.linear_model :no-arglists true])

(require-python '[pywebpush :bind-ns true :refer [webpush]])

(require-python '[werkzeug.utils :refer [secure_filename]])

(require-python '[google.cloud.secretmanager :as secretmanager :bind-ns true])

(require-python 'operator
                'base64
                'socket)

(require-python '[cryptography.fernet.Fernet :as Fernet :bind-ns true]
                '[cryptography.hazmat.primitives.hashes :as hashes :bind-ns true])

(require-python '[os :reload])

(defn test-bind-ns-usage []
  (pathlib/Path "/tmp")
  (Fernet/generate_key))

(defn test-refer-usage []
  (webpush {})
  (secure_filename "test.txt"))

(defn test-alias-usage []
  (np/array [1 2 3])
  (pd/DataFrame {})
  (secretmanager/SecretManagerServiceClient))

(defn test-python-builtins []
  (python/len [1 2 3])
  (python.list/append [] 1)
  (python.dict/keys {}))
