(ns libpython-clj.jna.protocols.buffer
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.v3.jna :as jna]
            [tech.v3.datatype.nio-buffer :as nio-buffer])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyBuffer]))


(def-pylib-fn PyObject_CheckBuffer
  "Return 1 if obj supports the buffer interface otherwise 0. When 1 is returned, it
  doesn’t guarantee that PyObject_GetBuffer() will succeed. This function always
  succeeds."
  Integer
  [obj ensure-pyobj])


;; /* Maximum number of dimensions */
(def PyBUF_MAX_NDIM 64)

;; /* Flags for getting buffers */
(def PyBUF_SIMPLE 0)
(def PyBUF_WRITABLE 0x0001)
;; /*  we used to include an E, backwards compatible alias  */
(def PyBUF_WRITEABLE PyBUF_WRITABLE)
(def PyBUF_FORMAT 0x0004)
(def PyBUF_ND 0x0008)
(def PyBUF_STRIDES (bit-or 0x0010 PyBUF_ND))
(def PyBUF_C_CONTIGUOUS (bit-or 0x0020 PyBUF_STRIDES))
(def PyBUF_F_CONTIGUOUS (bit-or 0x0040 PyBUF_STRIDES))
(def PyBUF_ANY_CONTIGUOUS (bit-or 0x0080 PyBUF_STRIDES))
(def PyBUF_INDIRECT (bit-or 0x0100 PyBUF_STRIDES))

(def PyBUF_CONTIG (bit-or PyBUF_ND PyBUF_WRITABLE))
(def PyBUF_CONTIG_RO PyBUF_ND)

(def PyBUF_STRIDED (bit-or PyBUF_STRIDES PyBUF_WRITABLE))
(def PyBUF_STRIDED_RO PyBUF_STRIDES)

(def PyBUF_RECORDS (bit-or PyBUF_STRIDES PyBUF_WRITABLE PyBUF_FORMAT))
(def PyBUF_RECORDS_RO (bit-or PyBUF_STRIDES PyBUF_FORMAT))

(def PyBUF_FULL (bit-or PyBUF_INDIRECT PyBUF_WRITABLE PyBUF_FORMAT))
(def PyBUF_FULL_RO (bit-or PyBUF_INDIRECT PyBUF_FORMAT))


(def PyBUF_READ  0x100)
(def PyBUF_WRITE 0x200)



(def-pylib-fn PyObject_GetBuffer
  "Send a request to exporter to fill in view as specified by flags. If the exporter
  cannot provide a buffer of the exact type, it MUST raise PyExc_BufferError, set
  view->obj to NULL and return -1.

   On success, fill in view, set view->obj to a new reference to exporter and return
   0. In the case of chained buffer providers that redirect requests to a single object,
   view->obj MAY refer to this object instead of exporter (See Buffer Object
   Structures).

   Successful calls to PyObject_GetBuffer() must be paired with calls to
   PyBuffer_Release(), similar to malloc() and free(). Thus, after the consumer is done
   with the buffer, PyBuffer_Release() must be called exactly once."
  Integer
  [exporter ensure-pyobj]
  [view (partial jna/ensure-type PyBuffer)]
  [flags int])



(def-pylib-fn PyBuffer_Release
  "Release the buffer view and decrement the reference count for view->obj. This
  function MUST be called when the buffer is no longer being used, otherwise reference
  leaks may occur.

   It is an error to call this function on a buffer that was not obtained via
   PyObject_GetBuffer()."
  nil
  [view (partial jna/ensure-type PyBuffer)])


(def-pylib-fn PyBuffer_IsContiguous
  "Return 1 if the memory defined by the view is C-style (order is 'C') or Fortran-style
  (order is 'F') contiguous or either one (order is 'A'). Return 0 otherwise. This
  function always succeeds."
  Integer
  [view (partial jna/ensure-type PyBuffer)]
  [order byte])



(def-pylib-fn PyBuffer_ToContiguous
  "Copy len bytes from src to its contiguous representation in buf. order can be 'C' or
  'F' (for C-style or Fortran-style ordering). 0 is returned on success, -1 on error.

   This function fails if len != src->len."
  Integer
  [buf nio-buffer/as-nio-buffer]
  [src (partial jna/ensure-type PyBuffer)]
  [len jna/size-t]
  [order byte])
