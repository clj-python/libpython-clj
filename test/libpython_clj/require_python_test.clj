(ns libpython-clj.require-python-test
  (:require [libpython-clj.require :as req :refer [require-python]
             :reload true]
            [libpython-clj.python :as py]
            [clojure.test :refer :all]
            [clojure.core.async :as a]))

;; Since this test mutates the global environment we have to accept that
;; it may not always work.

(require-python '[math
                  :refer :*
                  :exclude [sin cos]
                  :as pymath])

(deftest base-require-test
  (let [publics (ns-publics (find-ns 'libpython-clj.require-python-test))]
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
  (let [req-transform #'libpython-clj.require/req-transform]

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


(deftest import-python-test
  (is (= :ok (libpython-clj.require/import-python))))
