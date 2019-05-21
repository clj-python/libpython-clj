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
  (:import [com.sun.jna Pointer]))


int PyModule_Check(PyObject *p)

    Return true if p is a module object, or a subtype of a module object.



PyObject* PyModule_New(const char *name)
Return value: New reference.

Return a new module object with the __name__ attribute set to name. The module’s __name__, __doc__, __package__, and __loader__ attributes are filled in (all but __name__ are set to None); the caller is responsible for providing a __file__ attribute.

New in version 3.3.

Changed in version 3.4: __package__ and __loader__ are set to None.



PyObject* PyModule_GetDict(PyObject *module)¶
    Return value: Borrowed reference.

    Return the dictionary object that implements module’s namespace; this object is the same as the __dict__ attribute of the module object. If module is not a module object (or a subtype of a module object), SystemError is raised and NULL is returned.

    It is recommended extensions use other PyModule_*() and PyObject_*() functions rather than directly manipulate a module’s __dict__.



PyObject* PyModule_GetNameObject(PyObject *module)
    Return value: New reference.

    Return module’s __name__ value. If the module does not provide one, or if it is not a string, SystemError is raised and NULL is returned.

    New in version 3.3.



void* PyModule_GetState(PyObject *module)

    Return the “state” of the module, that is, a pointer to the block of memory allocated at module creation time, or NULL. See PyModuleDef.m_size.



PyModuleDef* PyModule_GetDef(PyObject *module)

    Return a pointer to the PyModuleDef struct from which the module was created, or NULL if the module wasn’t created from a definition.
