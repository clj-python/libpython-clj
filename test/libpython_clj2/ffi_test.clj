(ns libpython-clj2.ffi-test
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python :as py]
            [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.fn :as py-fn]
            [clojure.test :refer [deftest is]]))


(py/initialize!)


(deftest module-type-name-test
  (let [main-mod (py-ffi/with-gil (py-ffi/PyImport_AddModule "__main__"))
        mod-type (py-ffi/pyobject-type main-mod)
        mod-name (py-ffi/pytype-name mod-type)]
    (is (= "module" mod-name))))


(deftest pydir-basic
  (let [main-mod (py-ffi/with-gil (py-ffi/PyImport_AddModule "__main__"))
        dirdata (py-proto/dir main-mod)]
    (is (>= 7 (count dirdata)))))


(deftest error-handling
  (py-ffi/with-gil
    (is (thrown? Exception (py-ffi/run-simple-string "data = 1 +")))))


(deftest clj-fn
  (py-ffi/with-gil
    (let [pfn (py-fn/clj-fn->py-callable #(+ %1 %2))]
      (is (= 3 (py-proto/->jvm (py-fn/call pfn 1 2) nil))))))
