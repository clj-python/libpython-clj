(ns libpython-clj.jna.concrete.system
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.jna :as jna])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyObject]))


(def-pylib-fn void PySys_WriteStdout(const char *format, ...)
  "Write the output string described by format to sys.stdout. No exceptions are raised,
  even if truncation occurs (see below).

    format should limit the total size of the formatted output string to 1000 bytes or
    less – after 1000 bytes, the output string is truncated. In particular, this means
    that no unrestricted “%s” formats should occur; these should be limited using
    “%.<N>s” where <N> is a decimal number calculated so that <N> plus the maximum size
    of other formatted text does not exceed 1000 bytes. Also watch out for “%f”, which
    can print hundreds of digits for very large numbers.

    If a problem occurs, or sys.stdout is unset, the formatted message is written to the
    real (C level) stdout."
  nil
  [format str]
  [varags identity])



void PySys_WriteStderr(const char *format, ...)

    As PySys_WriteStdout(), but write to sys.stderr or stderr instead.
