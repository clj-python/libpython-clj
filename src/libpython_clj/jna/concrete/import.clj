(ns libpython-clj.jna.concrete.import
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


(def-pylib-fn PyImport_ImportModule
  "Return value: New reference.

   This is a simplified interface to PyImport_ImportModuleEx() below, leaving the
   globals and locals arguments set to NULL and level set to 0. When the name argument
   contains a dot (when it specifies a submodule of a package), the fromlist argument is
   set to the list ['*'] so that the return value is the named module rather than the
   top-level package containing it as would otherwise be the case. (Unfortunately, this
   has an additional side effect when name in fact specifies a subpackage instead of a
   submodule: the submodules specified in the package’s __all__ variable are loaded.)
   Return a new reference to the imported module, or NULL with an exception set on
   failure. A failing import of a module doesn’t leave the module in sys.modules.

   This function always uses absolute imports."
  Pointer
  [name str])


(def-pylib-fn PyImport_ImportModuleLevel
  "Return value: New reference.

    Similar to PyImport_ImportModuleLevelObject(), but the name is a UTF-8 encoded
    string instead of a Unicode object.

     Changed in version 3.3: Negative values for level are no longer accepted.


(documentation from python __import__ function
 __import__(name, globals=None, locals=None, fromlist=(), level=0)


This function is invoked by the import statement. It can be replaced (by importing the
builtins module and assigning to builtins.__import__) in order to change semantics of
the import statement, but doing so is strongly discouraged as it is usually simpler to
use import hooks (see PEP 302) to attain the same goals and does not cause issues with
code which assumes the default import implementation is in use. Direct use of
__import__() is also discouraged in favor of importlib.import_module().

The function imports the module name, potentially using the given globals and locals to
determine how to interpret the name in a package context. The fromlist gives the names
of objects or submodules that should be imported from the module given by name. The
standard implementation does not use its locals argument at all, and uses its globals
only to determine the package context of the import statement.

level specifies whether to use absolute or relative imports. 0 (the default) means only
perform absolute imports. Positive values for level indicate the number of parent
directories to search relative to the directory of the module calling __import__() (see
PEP 328 for the details).

When the name variable is of the form package.module, normally, the top-level package
(the name up till the first dot) is returned, not the module named by name. However,
when a non-empty fromlist argument is given, the module named by name is returned.

For example, the statement import spam results in bytecode resembling the following
code:

spam = __import__('spam', globals(), locals(), [], 0)

The statement import spam.ham results in this call:

spam = __import__('spam.ham', globals(), locals(), [], 0)

Note how __import__() returns the toplevel module here because this is the object that
is bound to a name by the import statement.

On the other hand, the statement from spam.ham import eggs, sausage as saus results in

_temp = __import__('spam.ham', globals(), locals(), ['eggs', 'sausage'], 0)
eggs = _temp.eggs
saus = _temp.sausage

Here, the spam.ham module is returned from __import__(). From this object, the names to
import are retrieved and assigned to their respective names.

If you simply want to import a module (potentially within a package) by name, use
importlib.import_module().

Changed in version 3.3: Negative values for level are no longer supported (which also
changes the default value to 0)."
  Pointer
  [name str]
  [globals ensure-pydict]
  [locals ensure-pydict]
  [fromlist ensure-pyobj]
  [level int])
