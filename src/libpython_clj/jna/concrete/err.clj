(ns libpython-clj.jna.concrete.err
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     find-pylib-symbol
                     *python-library*]
             :as libpy-base]
            [tech.jna :as jna])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna PyObject PyTypeObject]))


(def-pylib-fn PyErr_Occurred
  "Check if the error indicator is set.  If so, return the exception type."
  Pointer)


(def-pylib-fn PyErr_Clear
  "Clear the error indicator. If the error indicator is not set, there is no effect."
  nil)


(def-pylib-fn PyErr_PrintEx
  "Print a standard traceback to sys.stderr and clear the error indicator. Unless the
  error is a SystemExit. In that case the no traceback is printed and Python process
  will exit with the error code specified by the SystemExit instance.

   Call this function only when the error indicator is set. Otherwise it will cause a
   fatal error!

   If set_sys_last_vars is nonzero, the variables sys.last_type, sys.last_value and
   sys.last_traceback will be set to the type, value and traceback of the printed
   exception, respectively."
  nil
  [set-sys-last-vars int])


(def-pylib-fn PyErr_Print
  "Alias for PyErr_PrintEx(1)."
  nil)


(def-pylib-fn PyErr_WriteUnraisable
  "This utility function prints a warning message to sys.stderr when an exception has
   been set but it is impossible for the interpreter to actually raise the exception. It
   is used, for example, when an exception occurs in an __del__() method.

   The function is called with a single argument obj that identifies the context in
   which the unraisable exception occurred. If possible, the repr of obj will be printed
   in the warning message.

   An exception must be set when calling this function."
  nil
  [obj ensure-pyobj])


(def-pylib-fn PyErr_SetString
  "This is the most common way to set the error indicator. The first argument specifies
  the exception type; it is normally one of the standard exceptions,
  e.g. PyExc_RuntimeError. You need not increment its reference count. The second
  argument is an error message; it is decoded from 'utf-8’."
  nil
  [type ensure-pyobj]
  [message str])


(def-pylib-fn PyErr_SetObject
  "This function is similar to PyErr_SetString() but lets you specify an arbitrary
  Python object for the “value” of the exception."
  nil
  [type ensure-pyobj]
  [value ensure-pyobj])


(def-pylib-fn PyErr_SetNone
  "This is a shorthand for PyErr_SetObject(type, Py_None)."
  nil
  [type ensure-pyobj])


(def-pylib-fn PyErr_BadArgument
  "This is a shorthand for PyErr_SetString(PyExc_TypeError, message), where message
  indicates that a built-in operation was invoked with an illegal argument. It is mostly
  for internal use."
  Integer)



(def-pylib-fn PyErr_NoMemory
  "Return value: Always NULL.

   This is a shorthand for PyErr_SetNone(PyExc_MemoryError); it returns NULL so an
   object allocation function can write return PyErr_NoMemory(); when it runs out of
   memory."
  Pointer)


(def-pylib-fn PyErr_SetFromErrno
  "Return value: Always NULL.

   This is a convenience function to raise an exception when a C library function has
   returned an error and set the C variable errno. It constructs a tuple object whose
   first item is the integer errno value and whose second item is the corresponding
   error message (gotten from strerror()), and then calls PyErr_SetObject(type,
   object). On Unix, when the errno value is EINTR, indicating an interrupted system
   call, this calls PyErr_CheckSignals(), and if that set the error indicator, leaves it
   set to that. The function always returns NULL, so a wrapper function around a system
   call can write return PyErr_SetFromErrno(type); when the system call returns an
   error."
  Pointer
  [type ensure-pyobj])


(def-pylib-fn PyErr_BadInternalCall
  "This is a shorthand for PyErr_SetString(PyExc_SystemError, message), where message
  indicates that an internal operation (e.g. a Python/C API function) was invoked with
  an illegal argument. It is mostly for internal use."
  nil)


(def-pylib-fn PyErr_WarnEx
  "Issue a warning message. The category argument is a warning category (see below) or
  NULL; the message argument is a UTF-8 encoded string. stack_level is a positive number
  giving a number of stack frames; the warning will be issued from the currently
  executing line of code in that stack frame. A stack_level of 1 is the function calling
  PyErr_WarnEx(), 2 is the function above that, and so forth.

   Warning categories must be subclasses of PyExc_Warning; PyExc_Warning is a subclass
   of PyExc_Exception; the default warning category is PyExc_RuntimeWarning. The
   standard Python warning categories are available as global variables whose names are
   enumerated at Standard Warning Categories.

   For information about warning control, see the documentation for the warnings module
   and the -W option in the command line documentation. There is no C API for warning
   control."
  Integer
  [category ensure-pyobj]
  [message str]
  [stack_level jna/size-t])


(def-pylib-fn PyErr_WarnExplicit
  "Similar to PyErr_WarnExplicitObject() except that message and module are UTF-8
  encoded strings, and filename is decoded from the filesystem encoding (os.fsdecode())."
  Integer
  [category ensure-pyobj]
  [message str]
  [filename str]
  [lineno int]
  [module str]
  [registry ensure-pyobj])


(def-pylib-fn PyErr_Fetch
  "Retrieve the error indicator into three variables whose addresses are passed. If the
  error indicator is not set, set all three variables to NULL. If it is set, it will be
  cleared and you own a reference to each object retrieved. The value and traceback
  object may be NULL even when the type object is not.

   Note

   This function is normally only used by code that needs to catch exceptions or by code
   that needs to save and restore the error indicator temporarily, e.g.:

    {
       PyObject *type, *value, *traceback;
       PyErr_Fetch(&type, &value, &traceback);

       /* ... code that might produce other errors ... */

       PyErr_Restore(type, value, traceback);
    }"
  nil
  [ptype jna/ensure-ptr-ptr]
  [pvalue jna/ensure-ptr-ptr]
  [ptraceback jna/ensure-ptr-ptr])



(def-pylib-fn PyErr_Restore
  "Set the error indicator from the three objects. If the error indicator is already
   set, it is cleared first. If the objects are NULL, the error indicator is cleared. Do
   not pass a NULL type and non-NULL value or traceback. The exception type should be a
   class. Do not pass an invalid exception type or value. (Violating these rules will
   cause subtle problems later.) This call takes away a reference to each object: you
   must own a reference to each object before the call and after the call you no longer
   own these references. (If you don’t understand this, don’t use this function. I warned
   you.)

   Note

   This function is normally only used by code that needs to save and restore the error
   indicator temporarily. Use PyErr_Fetch() to save the current error indicator."
  [type ensure-pyobj]
  [value ensure-pyobj]
  [traceback ensure-pyobj])


(def-pylib-fn PyException_GetTraceback
  "Return value: New reference.

   Return the traceback associated with the exception as a new reference, as accessible
   from Python through __traceback__. If there is no traceback associated, this returns
   NULL."
  Pointer
  [ex ensure-pyobj])


(def-pylib-fn PyException_SetTraceback
  "Set the traceback associated with the exception to tb. Use Py_None to clear it."
  Integer
  [ex ensure-pyobj]
  [tb ensure-pyobj])


(def-pylib-fn PyException_GetContext
  "Return value: New reference.

   Return the context (another exception instance during whose handling ex was raised)
   associated with the exception as a new reference, as accessible from Python through
   __context__. If there is no context associated, this returns NULL."
  Pointer
  [ex ensure-pyobj])


(def-pylib-fn PyException_SetContext
  "Set the context associated with the exception to ctx. Use NULL to clear it. There is
  no type check to make sure that ctx is an exception instance. This steals a reference
  to ctx."
  nil
  [ex ensure-pyobj]
  [ctx ensure-pyobj])


(def-pylib-fn PyException_GetCause
  "Return value: New reference.

   Return the cause (either an exception instance, or None, set by raise ... from ...)
   associated with the exception as a new reference, as accessible from Python through
   __cause__."
  Pointer
  [ex ensure-pyobj])


(def-pylib-fn PyException_SetCause
  "Set the cause associated with the exception to cause. Use NULL to clear it. There is
  no type check to make sure that cause is either an exception instance or None. This
  steals a reference to cause.

   __suppress_context__ is implicitly set to True by this function."
  nil
  [ex ensure-pyobj]
  [cause ensure-pyobj])


(defmacro def-err-symbol
  [sym-name]
  `(defn ~sym-name
     []
     (->
      (find-pylib-symbol ~(name sym-name))
      (.getPointer 0))))


(def-err-symbol PyExc_BaseException)
(def-err-symbol PyExc_Exception)
(def-err-symbol PyExc_ArithmeticError)
(def-err-symbol PyExc_AssertionError)
(def-err-symbol PyExc_AttributeError)
(def-err-symbol PyExc_BlockingIOError)
(def-err-symbol PyExc_BrokenPipeError)
(def-err-symbol PyExc_BufferError)
(def-err-symbol PyExc_ChildProcessError)
(def-err-symbol PyExc_ConnectionAbortedError)
(def-err-symbol PyExc_ConnectionError)
(def-err-symbol PyExc_ConnectionRefusedError)
(def-err-symbol PyExc_ConnectionResetError)
(def-err-symbol PyExc_EOFError)
(def-err-symbol PyExc_FileExistsError)
(def-err-symbol PyExc_FileNotFoundError)
(def-err-symbol PyExc_FloatingPointError)
(def-err-symbol PyExc_GeneratorExit)
(def-err-symbol PyExc_ImportError)
(def-err-symbol PyExc_IndentationError)
(def-err-symbol PyExc_IndexError)
(def-err-symbol PyExc_InterruptedError)
(def-err-symbol PyExc_IsADirectoryError)
(def-err-symbol PyExc_KeyError)
(def-err-symbol PyExc_KeyboardInterrupt)
(def-err-symbol PyExc_LookupError)
(def-err-symbol PyExc_MemoryError)
(def-err-symbol PyExc_ModuleNotFoundError)
(def-err-symbol PyExc_NameError)
(def-err-symbol PyExc_NotADirectoryError)
(def-err-symbol PyExc_NotImplementedError)
(def-err-symbol PyExc_OSError)
(def-err-symbol PyExc_OverflowError)
(def-err-symbol PyExc_PermissionError)
(def-err-symbol PyExc_ProcessLookupError)
(def-err-symbol PyExc_RecursionError)
(def-err-symbol PyExc_ReferenceError)
(def-err-symbol PyExc_RuntimeError)
(def-err-symbol PyExc_StopAsyncIteration)
(def-err-symbol PyExc_StopIteration)
(def-err-symbol PyExc_SyntaxError)
(def-err-symbol PyExc_SystemError)
(def-err-symbol PyExc_SystemExit)
(def-err-symbol PyExc_TabError)
(def-err-symbol PyExc_TimeoutError)
(def-err-symbol PyExc_TypeError)
(def-err-symbol PyExc_UnboundLocalError)
(def-err-symbol PyExc_UnicodeDecodeError)
(def-err-symbol PyExc_UnicodeEncodeError)
(def-err-symbol PyExc_UnicodeError)
(def-err-symbol PyExc_UnicodeTranslateError)
(def-err-symbol PyExc_ValueError)
(def-err-symbol PyExc_ZeroDivisionError)


(def-err-symbol PyExc_Warning)
(def-err-symbol PyExc_BytesWarning)
(def-err-symbol PyExc_DeprecationWarning)
(def-err-symbol PyExc_FutureWarning)
(def-err-symbol PyExc_ImportWarning)
(def-err-symbol PyExc_PendingDeprecationWarning)
(def-err-symbol PyExc_ResourceWarning)
(def-err-symbol PyExc_RuntimeWarning)
(def-err-symbol PyExc_SyntaxWarning)
(def-err-symbol PyExc_UnicodeWarning)
(def-err-symbol PyExc_UserWarning)
