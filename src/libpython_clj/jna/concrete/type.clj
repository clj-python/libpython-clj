(ns libpython-clj.jna.concrete.type
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.v3.jna :as jna])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyMethodDef PyTypeObject PyObject]))


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

;;structmember

;; #define T_SHORT     0
;; #define T_INT       1
;; #define T_LONG      2
;; #define T_FLOAT     3
;; #define T_DOUBLE    4
;; #define T_STRING    5
;; #define T_OBJECT    6
;; /* XXX the ordering here is weird for binary compatibility */
;; #define T_CHAR      7   /* 1-character string */
;; #define T_BYTE      8   /* 8-bit signed int */
;; /* unsigned variants: */
;; #define T_UBYTE     9
;; #define T_USHORT    10
;; #define T_UINT      11
;; #define T_ULONG     12

;; /* Added by Jack: strings contained in the structure */
;; #define T_STRING_INPLACE    13

;; /* Added by Lillo: bools contained in the structure (assumed char) */
;; #define T_BOOL      14

;; #define T_OBJECT_EX 16  /* Like T_OBJECT, but raises AttributeError
;;                            when the value is NULL, instead of
;;                            converting to None. */
;; #define T_LONGLONG      17
;; #define T_ULONGLONG     18

;; #define T_PYSSIZET      19      /* Py_ssize_t */
;; #define T_NONE          20      /* Value is always None */


;; /* Flags */
;; #define READONLY            1
;; #define READ_RESTRICTED     2
;; #define PY_WRITE_RESTRICTED 4
;; #define RESTRICTED          (READ_RESTRICTED | PY_WRITE_RESTRICTED)



(def-pylib-fn PyType_Check
  "Return true if the object o is a type object, including instances of types derived
  from the standard type object. Return false in all other cases"
  Integer
  [o ensure-pyobj])


(def-pylib-fn PyType_GenericNew
  "Return value: New reference.

   Generic handler for the tp_new slot of a type object. Create a new instance using the
   type’s tp_alloc slot."
  Pointer
  [type (partial jna/ensure-type PyTypeObject)]
  [args #(when % (ensure-pyobj %))]
  [kwds #(when % (ensure-pyobj %))])


(def-pylib-fn PyType_Ready
  "Finalize a type object. This should be called on all type objects to finish their
  initialization. This function is responsible for adding inherited slots from a type’s
  base class. Return 0 on success, or return -1 and sets an exception on error."
  Integer
  [type (partial jna/ensure-type PyTypeObject)])


(def-pylib-fn _PyObject_New
  "Return value: New reference."
  Pointer
  [type (partial jna/ensure-type PyTypeObject)])


(def-pylib-fn PyObject_Del
  "Releases memory allocated to an object using PyObject_New() or
  PyObject_NewVar(). This is normally called from the tp_dealloc handler specified in
  the object’s type. The fields of the object should not be accessed after this call as
  the memory is no longer a valid Python object."
  Pointer
  [op ensure-pyobj])
