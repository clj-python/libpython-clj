(ns libpython-clj.jna.concrete.unicode
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     find-pylib-symbol
                     *python-library*]
             :as libpy-base]
            [tech.jna :as jna]
            [tech.v2.datatype :as dtype])
  (:import [com.sun.jna Pointer]
           [com.sun.jna.ptr PointerByReference
            LongByReference IntByReference]
           [libpython_clj.jna PyObject]))



(def-pylib-fn PyUnicode_Decode
  "Return value: New reference.

   Create a Unicode object by decoding size bytes of the encoded string s. encoding and
   errors have the same meaning as the parameters of the same name in the str() built-in
   function. The codec to be used is looked up using the Python codec registry. Return
   NULL if an exception was raised by the codec.

  Signature:
  PyObject* (const char *s, Py_ssize_t size, const char *encoding, const char *errors)"
  PyObject
  [s dtype/as-nio-buffer]
  [size jna/size-t]
  [encoding str]
  [errors str])


(def-pylib-fn PyUnicode_AsEncodedString
  "Return value: New reference.

   Encode a Unicode object and return the result as Python bytes object. encoding and
   errors have the same meaning as the parameters of the same name in the Unicode
   encode() method. The codec to be used is looked up using the Python codec
   registry. Return NULL if an exception was raised by the codec.
   Signature:
   PyObject* (PyObject *unicode, const char *encoding, const char *errors)"
  PyObject
  [s ensure-pyobj]
  [encoding str]
  [errors str])


(defn size-t-by-reference-type
  []
  (if (instance? Long (jna/size-t 0))
    LongByReference
    IntByReference))


(def-pylib-fn PyUnicode_AsUTF8AndSize
  "Return a pointer to the UTF-8 encoding of the Unicode object, and store the size of
   the encoded representation (in bytes) in size. The size argument can be NULL; in this
   case no size will be stored. The returned buffer always has an extra null byte
   appended (not included in size), regardless of whether there are any other null code
   points.

   In the case of an error, NULL is returned with an exception set and no size is stored.

   This caches the UTF-8 representation of the string in the Unicode object, and
   subsequent calls will return a pointer to the same buffer. The caller is not
   responsible for deallocating the buffer.

   New in version 3.3.

   Changed in version 3.7: The return type is now const char * rather of char *."
  Pointer
  [py-obj ensure-pyobj]
  [size-ptr (partial jna/ensure-type (size-t-by-reference-type))])


(def-pylib-fn PyUnicode_AsUTF8
  "As PyUnicode_AsUTF8AndSize(), but does not store the size.

   New in version 3.3.

   Changed in version 3.7: The return type is now const char * rather of char *."
  Pointer
  [py-obj ensure-pyobj])
