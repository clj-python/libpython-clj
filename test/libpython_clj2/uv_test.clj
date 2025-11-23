(ns libpython-clj2.uv-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [libpython-clj2.python.uv :as uv]))


(deftest start-and-print-captures-stdout
  (let [out (with-out-str
              (@#'libpython-clj2.python.uv/start-and-print!
               ["sh" "-lc" "printf 'hello\n' "]))]
    (is (= "hello\n" out))))

(deftest start-and-print-captures-stderr
  (let [out (with-out-str
              (@#'libpython-clj2.python.uv/start-and-print!
               ["sh" "-lc" "printf 'oops\n' 1>&2 "]))]
    (is (= "oops\n" out))))

(deftest start-and-print-reads-stdout-then-stderr
  ;; The child process writes interleaved to stdout and stderr, but
  ;; start-and-print! reads stdout to completion before stderr, so the
  ;; captured output should have all stdout first, then all stderr.
  (let [script (str/join "; " ["echo out1"
                                 "echo err1 1>&2"
                                 "echo out2"
                                 "echo err2 1>&2"]) 
        out (with-out-str
              (@#'libpython-clj2.python.uv/start-and-print!
               ["sh" "-lc" script]))]
    (is (= (str "out1\n"
                "out2\n"
                "err1\n"
                "err2\n")
           out))))
