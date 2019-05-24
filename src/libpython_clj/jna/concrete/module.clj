(ns libpython-clj.jna.concrete.module
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
           [libpython_clj.jna PyModuleDef PyMethodDef PyObject]))


(def-pylib-fn PyModule_Check
  "Return true if p is a module object, or a subtype of a module object."
  Integer
  [p ensure-pyobj])


(def-pylib-fn PyModule_New
  "Return value: New reference.

   Return a new module object with the __name__ attribute set to name. The module’s
   __name__, __doc__, __package__, and __loader__ attributes are filled in (all but
   __name__ are set to None); the caller is responsible for providing a __file__
   attribute.

   New in version 3.3.

   Changed in version 3.4: __package__ and __loader__ are set to None."
  Pointer
  [name str])


(def-pylib-fn PyModule_SetDocString
  "Set the docstring for module to docstring. This function is called automatically when
  creating a module from PyModuleDef, using either PyModule_Create or
  PyModule_FromDefAndSpec.

   New in version 3.5."
  Integer
  [module ensure-pyobj]
  [docstring str])


(def-pylib-fn PyModule_AddFunctions
  "Add the functions from the NULL terminated functions array to module. Refer to the
  PyMethodDef documentation for details on individual entries (due to the lack of a
  shared module namespace, module level “functions” implemented in C typically receive
  the module as their first parameter, making them similar to instance methods on Python
  classes). This function is called automatically when creating a module from
  PyModuleDef, using either PyModule_Create or PyModule_FromDefAndSpec."
  Integer
  [module ensure-pyobj]
  [functions (partial jna/ensure-type PyMethodDef)])


(def-pylib-fn PyModule_GetDict
  "Return value: Borrowed reference.

   Return the dictionary object that implements module’s namespace; this object is the
   same as the __dict__ attribute of the module object. If module is not a module
   object (or a subtype of a module object), SystemError is raised and NULL is
   returned.

   It is recommended extensions use other PyModule_*() and PyObject_*() functions
   rather than directly manipulate a module’s __dict__."
  Pointer
  [module ensure-pyobj])


(def-pylib-fn PyModule_GetNameObject
  "Return value: New reference.

   Return module’s __name__ value. If the module does not provide one, or if it is not a
   string, SystemError is raised and NULL is returned.

   New in version 3.3."
  Pointer
  [module ensure-pyobj])


(def-pylib-fn PyModule_GetState
  "Return the “state” of the module, that is, a pointer to the block of memory allocated
  at module creation time, or NULL. See PyModuleDef.m_size."
  Pointer
  [module ensure-pyobj])


(def-pylib-fn PyModule_GetDef
  "Return a pointer to the PyModuleDef struct from which the module was created, or NULL
  if the module wasn’t created from a definition."
  PyModuleDef
  [module ensure-pyobj])


(def-pylib-fn PyModule_AddObject
  "Add an object to module as name. This is a convenience function which can be used
  from the module’s initialization function. This steals a reference to value. Return -1
  on error, 0 on success."
  Integer
  [module ensure-pyobj]
  [name str]
  [value ensure-pyobj])


(def-pylib-fn PyModule_AddIntConstant
  "Add an integer constant to module as name. This convenience function can be used from
  the module’s initialization function. Return -1 on error, 0 on success."
  Integer
  [module ensure-pyobj]
  [name str]
  [value int])


(def-pylib-fn PyModule_AddStringConstant
  "Add a string constant to module as name. This convenience function can be used from
  the module’s initialization function. The string value must be NULL-terminated. Return
  -1 on error, 0 on success."
  Integer
  [module ensure-pyobj]
  [name str]
  [value str])
