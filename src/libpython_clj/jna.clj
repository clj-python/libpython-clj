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
            [libpython-clj.jna.base]
            [libpython-clj.jna.interpreter]
            [libpython-clj.jna.protocols.object]
            [libpython-clj.jna.protocols.iterator]
            [libpython-clj.jna.protocols.sequence]
            [libpython-clj.jna.protocols.mapping]
            [libpython-clj.jna.protocols.buffer]
            [libpython-clj.jna.concrete.numeric.integer]
            [libpython-clj.jna.concrete.numeric.float]
            [libpython-clj.jna.concrete.numeric.boolean]
            [libpython-clj.jna.concrete.numeric.complex]
            [libpython-clj.jna.concrete.tuple]
            [libpython-clj.jna.concrete.list]
            [libpython-clj.jna.concrete.set]
            [libpython-clj.jna.concrete.dict]
            [libpython-clj.jna.concrete.unicode]
            [libpython-clj.jna.concrete.cfunction]
            [libpython-clj.jna.concrete.import]
            [libpython-clj.jna.concrete.module]
            [libpython-clj.jna.concrete.type]
            [libpython-clj.jna.concrete.err])
  (:import [com.sun.jna Pointer NativeLibrary]))


(export-symbols libpython-clj.jna.base
                find-pylib-symbol)


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
                get-type-name
                lookup-type-symbols
                Py_None
                Py_NotImplemented
                PyMem_Free
                PyEval_RestoreThread
                PyEval_SaveThread
                PyThreadState_Get
                PyThreadState_Swap
                PyRun_String
                PyRun_StringFlags)


(export-symbols libpython-clj.jna.protocols.object
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


(export-symbols libpython-clj.jna.protocols.iterator
                PyIter_Check
                PyIter_Next)


(export-symbols libpython-clj.jna.protocols.sequence
                PySequence_Check
                PySequence_Concat
                PySequence_Contains
                PySequence_Count
                PySequence_DelItem
                PySequence_DelSlice
                PySequence_GetItem
                PySequence_GetSlice
                PySequence_InPlaceConcat
                PySequence_InPlaceRepeat
                PySequence_Index
                PySequence_Length
                PySequence_List
                PySequence_Repeat
                PySequence_SetItem
                PySequence_SetSlice
                PySequence_Tuple)


(export-symbols libpython-clj.jna.protocols.mapping
                PyMapping_Check
                PyMapping_DelItem
                PyMapping_DelItemString
                PyMapping_GetItemString
                PyMapping_HasKey
                PyMapping_HasKeyString
                PyMapping_Items
                PyMapping_Keys
                PyMapping_Length
                PyMapping_SetItemString
                PyMapping_Values)


(export-symbols libpython-clj.jna.protocols.buffer
                PyBUF_ANY_CONTIGUOUS
                PyBUF_CONTIG
                PyBUF_CONTIG_RO
                PyBUF_C_CONTIGUOUS
                PyBUF_FORMAT
                PyBUF_FULL
                PyBUF_FULL_RO
                PyBUF_F_CONTIGUOUS
                PyBUF_INDIRECT
                PyBUF_MAX_NDIM
                PyBUF_ND
                PyBUF_READ
                PyBUF_RECORDS
                PyBUF_RECORDS_RO
                PyBUF_SIMPLE
                PyBUF_STRIDED
                PyBUF_STRIDED_RO
                PyBUF_STRIDES
                PyBUF_WRITABLE
                PyBUF_WRITE
                PyBUF_WRITEABLE
                PyBuffer_IsContiguous
                PyBuffer_Release
                PyBuffer_ToContiguous
                PyObject_CheckBuffer
                PyObject_GetBuffer)


(export-symbols libpython-clj.jna.concrete.numeric.integer
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


(export-symbols libpython-clj.jna.concrete.numeric.float
                PyFloat_AsDouble
                PyFloat_Check
                PyFloat_CheckExact
                PyFloat_FromDouble
                PyFloat_FromString
                PyFloat_GetInfo
                PyFloat_GetMax
                PyFloat_GetMin)


(export-symbols libpython-clj.jna.concrete.numeric.boolean
                PyBool_Check
                PyBool_FromLong
                Py_False
                Py_True)


(export-symbols libpython-clj.jna.concrete.numeric.complex
                PyComplex_AsCComplex
                PyComplex_Check
                PyComplex_FromCComplex
                PyComplex_FromDoubles
                PyComplex_ImagAsDouble
                PyComplex_RealAsDouble)


(export-symbols libpython-clj.jna.concrete.tuple
                PyTuple_Check
                PyTuple_GetItem
                PyTuple_GetSlice
                PyTuple_New
                PyTuple_SetItem)


(export-symbols libpython-clj.jna.concrete.list
                PyList_Append
                PyList_AsTuple
                PyList_Check
                PyList_GetItem
                PyList_GetSlice
                PyList_Insert
                PyList_New
                PyList_Reverse
                PyList_SetItem
                PyList_SetSlice
                PyList_Size
                PyList_Sort)


(export-symbols libpython-clj.jna.concrete.set
                PyFrozenSet_Check
                PyFrozenSet_New
                PySet_Add
                PySet_Check
                PySet_Clear
                PySet_Contains
                PySet_Discard
                PySet_New
                PySet_Pop)


(export-symbols libpython-clj.jna.concrete.dict
                PyDictProxy_New
                PyDict_Check
                PyDict_Clear
                PyDict_Contains
                PyDict_Copy
                PyDict_DelItem
                PyDict_DelItemString
                PyDict_GetItem
                PyDict_GetItemString
                PyDict_GetItemWithError
                PyDict_Items
                PyDict_Keys
                PyDict_Merge
                PyDict_MergeFromSeq2
                PyDict_New
                PyDict_Next
                PyDict_SetDefault
                PyDict_SetItem
                PyDict_SetItemString
                PyDict_Size
                PyDict_Update
                PyDict_Values)


(export-symbols libpython-clj.jna.concrete.unicode
                PyUnicode_AsEncodedString
                PyUnicode_AsUTF8AndSize
                PyUnicode_AsUTF8
                PyUnicode_Decode)


(export-symbols libpython-clj.jna.concrete.cfunction
                METH_CLASS
                METH_COEXIST
                METH_KEYWORDS
                METH_NOARGS
                METH_O
                METH_STATIC
                METH_VARARGS
                PyCFunction_New)


(export-symbols libpython-clj.jna.concrete.import
                PyImport_ImportModule
                PyImport_Import
                PyImport_AddModule
                PyImport_ImportModuleLevel)


(export-symbols libpython-clj.jna.concrete.type
                PyObject_Del
                PyType_Check
                PyType_GenericNew
                PyType_Ready
                Py_TPFLAGS_BASETYPE
                Py_TPFLAGS_DEFAULT
                Py_TPFLAGS_HAVE_GC
                Py_TPFLAGS_HAVE_STACKLESS_EXTENSION
                Py_TPFLAGS_HAVE_VERSION_TAG
                Py_TPFLAGS_HEAPTYPE
                Py_TPFLAGS_IS_ABSTRACT
                Py_TPFLAGS_READY
                Py_TPFLAGS_READYING
                Py_TPFLAGS_VALID_VERSION_TAG
                _PyObject_New)


(export-symbols libpython-clj.jna.concrete.module
                PyModule_AddFunctions
                PyModule_AddIntConstant
                PyModule_AddObject
                PyModule_AddStringConstant
                PyModule_Check
                PyModule_GetDef
                PyModule_GetDict
                PyModule_GetNameObject
                PyModule_GetState
                PyModule_New
                PyModule_SetDocString)


(export-symbols libpython-clj.jna.concrete.err
                PyErr_BadArgument
                PyErr_BadInternalCall
                PyErr_Clear
                PyErr_Fetch
                PyErr_NoMemory
                PyErr_Print
                PyErr_PrintEx
                PyErr_Restore
                PyErr_SetFromErrno
                PyErr_SetNone
                PyErr_SetObject
                PyErr_SetString
                PyErr_WarnEx
                PyErr_WarnExplicit
                PyErr_WriteUnraisable
                ;; Exception classes
                PyExc_ArithmeticError
                PyExc_AssertionError
                PyExc_AttributeError
                PyExc_BaseException
                PyExc_BlockingIOError
                PyExc_BrokenPipeError
                PyExc_BufferError
                PyExc_BytesWarning
                PyExc_ChildProcessError
                PyExc_ConnectionAbortedError
                PyExc_ConnectionError
                PyExc_ConnectionRefusedError
                PyExc_ConnectionResetError
                PyExc_DeprecationWarning
                PyExc_EOFError
                PyExc_Exception
                PyExc_FileExistsError
                PyExc_FileNotFoundError
                PyExc_FloatingPointError
                PyExc_FutureWarning
                PyExc_GeneratorExit
                PyExc_ImportError
                PyExc_ImportWarning
                PyExc_IndentationError
                PyExc_IndexError
                PyExc_InterruptedError
                PyExc_IsADirectoryError
                PyExc_KeyError
                PyExc_KeyboardInterrupt
                PyExc_LookupError
                PyExc_MemoryError
                PyExc_ModuleNotFoundError
                PyExc_NameError
                PyExc_NotADirectoryError
                PyExc_NotImplementedError
                PyExc_OSError
                PyExc_OverflowError
                PyExc_PendingDeprecationWarning
                PyExc_PermissionError
                PyExc_ProcessLookupError
                PyExc_RecursionError
                PyExc_ReferenceError
                PyExc_ResourceWarning
                PyExc_RuntimeError
                PyExc_RuntimeWarning
                PyExc_StopAsyncIteration
                PyExc_StopIteration
                PyExc_SyntaxError
                PyExc_SyntaxWarning
                PyExc_SystemError
                PyExc_SystemExit
                PyExc_TabError
                PyExc_TimeoutError
                PyExc_TypeError
                PyExc_UnboundLocalError
                PyExc_UnicodeDecodeError
                PyExc_UnicodeEncodeError
                PyExc_UnicodeError
                PyExc_UnicodeTranslateError
                PyExc_UnicodeWarning
                PyExc_UserWarning
                PyExc_ValueError
                PyExc_Warning
                PyExc_ZeroDivisionError
                ;;Exception data system
                PyException_GetCause
                PyException_GetContext
                PyException_GetTraceback
                PyException_SetCause
                PyException_SetContext
                PyException_SetTraceback)
