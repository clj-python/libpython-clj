(ns libpython-clj.jna.concrete.bytes
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     as-pyobj
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.v3.datatype.nio-buffer :as nio-buffer]
            [tech.v3.jna :as jna])
  (:import [com.sun.jna Pointer]))



(def-pylib-fn PyBytes_Check
  "Return true if the object o is a bytes object or an instance of a subtype of the
  bytes type."
  Integer
  [o ensure-pyobj])


(def-pylib-fn PyBytes_CheckExact
  "Return true if the object o is a bytes object, but not an instance of a subtype of
  the bytes type."
  Integer
  [o ensure-pyobj])



(def-pylib-fn PyBytes_FromString
  "Return value: New reference.

   Return a new bytes object with a copy of the string v as value on success, and NULL
   on failure. The parameter v must not be NULL; it will not be checked."
  Pointer
  [v str])


(def-pylib-fn PyBytes_FromStringAndSize
  "Return value: New reference.

   Return a new bytes object with a copy of the string v as value and length len on
   success, and NULL on failure. If v is NULL, the contents of the bytes object are
   uninitialized."
  Pointer
  [v nio-buffer/->nio-buffer]
  [len jna/size-t])


(def-pylib-fn PyBytes_AsString
  "Return a pointer to the contents of o. The pointer refers to the internal buffer of
  o, which consists of len(o) + 1 bytes. The last byte in the buffer is always null,
  regardless of whether there are any other null bytes. The data must not be modified in
  any way, unless the object was just created using PyBytes_FromStringAndSize(NULL,
  size). It must not be deallocated. If o is not a bytes object at all,
  PyBytes_AsString() returns NULL and raises TypeError."
  String
  [o ensure-pyobj])


(def-pylib-fn PyBytes_AsStringAndSize
  "Return the null-terminated contents of the object obj through the output variables
  buffer and length.

   If length is NULL, the bytes object may not contain embedded null bytes; if it does,
   the function returns -1 and a ValueError is raised.

   The buffer refers to an internal buffer of obj, which includes an additional null
   byte at the end (not counted in length). The data must not be modified in any way,
   unless the object was just created using PyBytes_FromStringAndSize(NULL, size). It
   must not be deallocated. If obj is not a bytes object at all,
   PyBytes_AsStringAndSize() returns -1 and raises TypeError.

   Changed in version 3.5: Previously, TypeError was raised when embedded null bytes
   were encountered in the bytes object.

   Signature:
   int (PyObject *obj, char **buffer, Py_ssize_t *length)"
  Integer
  [obj ensure-pyobj]
  [ptr-to-buffer jna/ensure-ptr-ptr]
  [length as-pyobj])



(def-pylib-fn PyBytes_Concat
  "Create a new bytes object in *bytes containing the contents of newpart appended to
  bytes; the caller will own the new reference. The reference to the old value of bytes
  will be stolen. If the new object cannot be created, the old reference to bytes will
  still be discarded and the value of *bytes will be set to NULL; the appropriate
  exception will be set.
  Signature:
  void (PyObject **bytes, PyObject *newpart)"
  nil
  [out-bytes jna/ensure-ptr-ptr]
  [newpart ensure-pyobj])



(def-pylib-fn PyBytes_ConcatAndDel
  "Create a new bytes object in *bytes containing the contents of newpart appended to
  bytes. This version decrements the reference count of newpart.
  Signature:
  void (PyObject **bytes, PyObject *newpart)"
  nil
  [out-bytes jna/ensure-ptr-ptr]
  [newpart ensure-pyobj])
