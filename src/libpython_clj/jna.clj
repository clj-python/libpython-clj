(ns libpython-clj.jna
  (:require [tech.v2.datatype :as dtype]
            [tech.jna :as jna]
            [tech.jna.base :as jna-base]
            [tech.v2.datatype.typecast :as typecast]
            [camel-snake-kebab.core :refer [->kebab-case]]
            [tech.parallel.utils :refer [export-symbols]]
            [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj]]
            [libpython-clj.jna.interpreter]
            [libpython-clj.jna.object]
            [libpython-clj.jna.numeric.integer])
  (:import [com.sun.jna Pointer NativeLibrary]))


(export-symbols libpython-clj.jna.interpreter
                Py_InitializeEx
                Py_IsInitialized
                Py_FinalizeEx
                PyRun_SimpleString
                PyErr_Clear
                PyErr_Occurred
                PyErr_PrintEx
                PyErr_Print
                PyErr_WriteUnraisable
                PySys_SetArgv
                lookup-type-symbols
                Py_None
                Py_True
                Py_False
                Py_NotImplemented
                PyMem_Free
                PyEval_RestoreThread
                PyEval_SaveThread
                PyThreadState_Get
                PyThreadState_Swap
                PyRun_String)


(export-symbols libpython-clj.jna.object
                Py_DecRef
                Py_IncRef
                PyObject_Type
                PyObject_Repr
                PyObject_Str
                PyObject_HasAttr
                PyObject_HasAttrString
                PyObject_GetAttr
                PyObject_GetAttrString
                PyObject_GenericGetAttr
                PyObject_SetAttr
                PyObject_SetAttrString
                PyObject_GenericSetAttr
                PyObject_DelAttr
                PyObject_DelAttrString
                PyObject_GenericGetDict
                PyObject_GenericSetDict
                PyObject_RichCompare
                PyObject_RichCompareBool
                PyCallable_Check
                PyObject_Call
                PyObject_CallObject
                PyObject_Hash
                PyObject_IsTrue
                PyObject_Not
                PyObject_Length
                PyObject_GetItem
                PyObject_SetItem
                PyObject_DelItem
                PyObject_Dir
                PyObject_GetIter)


(export-symbols libpython-clj.jna.numeric.integer
                PyLong_Check
                PyLong_CheckExact
                PyLong_FromLong
                PyLong_FromUnsignedLong
                PyLong_FromSsize_t
                PyLong_FromLongLong
                PyLong_FromUnsignedLongLong
                PyLong_FromDouble
                PyLong_AsLong
                PyLong_AsLongLong)



(def-pylib-fn PyBool_Check
  "Return true if o is of type PyBool_Type."
  Integer
  [py-obj ensure-pyobj])


(def-pylib-fn PyBool_FromLong
  "Return value: New reference.
    Return a new reference to Py_True or Py_False depending on the truth value of v."
  Pointer
  [v long])


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
  [size-ptr jna/as-ptr])


(def-pylib-fn PyUnicode_AsUTF8
  "As PyUnicode_AsUTF8AndSize(), but does not store the size.

   New in version 3.3.

   Changed in version 3.7: The return type is now const char * rather of char *."
  Pointer
  [py-obj ensure-pyobj])
