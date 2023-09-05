(ns libpython-clj2.require-python-test
  (:require [libpython-clj2.require :as req
             :refer [require-python pydafy pynav]
             :reload true]
            [libpython-clj2.python :as py]
            [clojure.test :refer :all]
            [clojure.datafy :refer [datafy nav]]))

;; Since this test mutates the global environment we have to accept that
;; it may not always work.

(require-python '[math
                  :refer :*
                  :exclude [sin cos]
                  :as pymath])

(deftest base-require-test
  (let [publics (ns-publics (find-ns 'libpython-clj2.require-python-test))]
    (is (not (contains? publics 'sin)))
    (is (= 10.0 (double (floor 10.1))))
    (is (thrown? Throwable (require-python '[math :refer [blah]])))))

(deftest parse-flags-test
  ;; sanity check
  (is (= #{:reload :foo}
         #{:foo :reload}))
  (let [parse-flags #'req/parse-flags]
    (is (= #{:reload} (parse-flags
                       #{:reload}
                       '[:reload foo])))
    (is (= #{:reload} (parse-flags #{:reload} '[:reload true])))
    (is (= #{:reload} (parse-flags #{:reload} '[:reload :as foo])))
    (is (= #{:reload} (parse-flags #{:reload} '[:reload foo :as])))
    (is (= #{:reload} (parse-flags #{:reload} '[foo :reload :as bar])))
    (is (= #{} (parse-flags #{:reload} '[:reload false])))
    (is (= #{:reload} (parse-flags #{:reload} '[:reload false :reload])))
    (is (= #{:reload} (parse-flags #{:reload} '[:reload false :reload true])))
    (is (= #{} (parse-flags  #{:reload} '[:reload :reload false])))
    (is (= #{} (parse-flags #{:reload} '[:reload true :reload false])))
    (is (= #{:a :b} (parse-flags #{:a :b} '[:a true :b])))
    (is (= #{:a :b} (parse-flags #{:a :b} '[:a :b])))
    (is (= #{:a :b} (parse-flags #{:a :b} '[:a true :b])))
    (is (= #{:a :b} (parse-flags #{:a :b} '[:a  :b true])))
    (is (= #{:a :b} (parse-flags #{:a :b} '[:a  true :b true])))
    (is (= #{:b} (parse-flags #{:a :b} '[:a  false :b true])))
    (is (= #{:b} (parse-flags #{:a :b} '[:b true :a false])))
    (is (= #{:b} (parse-flags #{:a :b} '[:b :a false])))
    (is (= #{:b :a} (parse-flags #{:a :b} '[:b :a false :a true])))))


(require-python '[builtins :as python]
                '[builtins.list :as python.pylist])

;; NOTE -- even though builtins.list has been aliased to
;; to python.pylist, you are still required to require
;; "builtins as python" in order to construct a list

(deftest require-python-classes-with-alias-test
  (let [l (python/list)]
    (is (= (vec l) []))
    (python.pylist/append l 1)
    (is (= (vec l) [1]))
    (python.pylist/append l 3)
    (is (= (vec l) [1 3]))
    (python.pylist/append l 5)
    (is (= (vec l) [1 3 5]))
    (python.pylist/clear l)
    (is (= (python/list) (vec l)))))

(require-python 'csv.DictWriter)

(deftest require-python-classes
  ;; simple creation/recall test
  (is csv.DictWriter/__init__)
  (is csv.DictWriter/writeheader)
  (is csv.DictWriter/writerow)
  (is csv.DictWriter/writerows))

(deftest test-req-transform
  (let [req-transform #'libpython-clj2.require/req-transform]

    (is (= (req-transform 'a) 'a))

    (is (= (req-transform '(a b c))
           '(a.b a.c)))

    (is (= (req-transform '(a [b :as c :refer [blah]]))
           (list '[a.b :as c :refer [blah]])))

    (is (= (req-transform  '(a [b :as c :refer [x] :flagA] d))
           (list '[a.b :as c :refer [x] :flagA] 'a.d)))

    (is (= (req-transform 'a 'b)
           'a.b))

    (is (= (req-transform 'a.b 'c)
           'a.b.c))

    (is (thrown? Exception (req-transform 'a.b 'c.d)))

    (is (= (req-transform 'a '[b]) '[a.b]))

    (is (= (req-transform 'a '[b :as c :refer [blah] :flagA])
           '[a.b :as c :refer [blah] :flagA]))))

(deftest datafy-test

  (defmethod pydafy 'builtins.list [x]
    (vec x))

  (is (vector? (datafy (python/list [1 2 3]))))

  (defmethod pydafy 'builtins.frozenset [x]
    (set x))

  (is (set? (datafy (python/frozenset [1 2 3])))))

(deftest import-python-test
  (is (= :ok (libpython-clj2.require/import-python))))

(require-python '[socket :bind-ns true])
(require-python 'socket.SocketIO)
(deftest metadata-test
  (testing "metadata generation"
    ;; module meta
    (let [{line :line file :file} (meta #'socket)]
      (is (= 1 line)
          "Modules have line numbers")
      (is (string? file)
          "Modules have file paths"))
    ;; class meta
    (let [{line :line file :file} (meta #'socket/SocketIO)]
      (is (int? line)
          "Classes have line numbers")
      (is (string? file)
          "Classes have file paths"))
    ;; function meta
    (let [{line :line file :file} (meta #'socket/_intenum_converter)]
      (is (int? line)
          "Functions have line numbers")
      (is (string? file)
          "Functions have file paths"))
    ;; method meta
    (let [{line :line file :file} (meta #'socket.SocketIO/readable)]
      (is (int? line)
          "Methods have line numbers")
      (is (string? file)
          "Methods have file paths"))))

(deftest convert-types
  (let [typing-module (py/import-module "typing")]
    (testing "convert generic alias type to JVM"
      (-> (py/py. typing-module GenericAlias list str)
          py/->jvm
          :type
          (= :generic-alias)
          is))))
