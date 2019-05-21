(ns libpython_clj.jna.concrete.type
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.jna :as jna])
  (:import [com.sun.jna Pointer]))


(def Py_TPFLAGS_HEAPTYPE (bit-shift-left 1 9))

;; /* Set if the type allows subclassing */
(def Py_TPFLAGS_BASETYPE (bit-shift-left 1 10))

;; /* Set if the type is 'ready' -- fully initialized */
(def Py_TPFLAGS_READY (bit-shift-left 1 12))

;; /* Set while the type is being 'readied', to prevent recursive ready calls */
(def Py_TPFLAGS_READYING (bit-shift-left 1 13))

;; /* Objects support garbage collection (see objimp.h) */
(def Py_TPFLAGS_HAVE_GC (bit-shift-left 1 14))

;; /* These two bits are preserved for Stackless Python, next after this is 17 */
;;We aren't going there
;; #ifdef STACKLESS
;; #define Py_TPFLAGS_HAVE_STACKLESS_EXTENSION (3UL << 15)
;; #else
(def Py_TPFLAGS_HAVE_STACKLESS_EXTENSION 0)
;; #endif

;; /* Objects support type attribute cache */
(def Py_TPFLAGS_HAVE_VERSION_TAG (bit-shift-left 1 18))
(def Py_TPFLAGS_VALID_VERSION_TAG (bit-shift-left 1 19))

;; /* Type is abstract and cannot be instantiated */
(def Py_TPFLAGS_IS_ABSTRACT (bit-shift-left 1 20))

;; /* These flags are used to determine if a type is a subclass. */
;; #define Py_TPFLAGS_LONG_SUBCLASS        (1UL << 24)
;; #define Py_TPFLAGS_LIST_SUBCLASS        (1UL << 25)
;; #define Py_TPFLAGS_TUPLE_SUBCLASS       (1UL << 26)
;; #define Py_TPFLAGS_BYTES_SUBCLASS       (1UL << 27)
;; #define Py_TPFLAGS_UNICODE_SUBCLASS     (1UL << 28)
;; #define Py_TPFLAGS_DICT_SUBCLASS        (1UL << 29)
;; #define Py_TPFLAGS_BASE_EXC_SUBCLASS    (1UL << 30)
;; #define Py_TPFLAGS_TYPE_SUBCLASS        (1UL << 31)

(def Py_TPFLAGS_DEFAULT  (bit-or Py_TPFLAGS_HAVE_STACKLESS_EXTENSION
                                 Py_TPFLAGS_HAVE_VERSION_TAG))
