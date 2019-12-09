# Time for a ChangeLog!

## 1.20

With two many huge things we had to skip a few versions!

#### require-python

`require-python` works like require but it works on python modules.
`require-python` dynamically loads the module and exports it's symbols into
a clojure namespace.  There are many options available for this pathway.


This implements a big step towards embedding python in Clojure in a simple,
clear, and easy to use way.  One important thing to consider is the require
has a `:reload:` option to allow you to actively develop a python module and
test it via clojure.


This excellent work was in large part done by [James Tolton](https://github.com/jjtolton).


* [require-python-test](test/libpython_clj/require_python_test.clj)


#### Clojure-defined Python classes

You can now extend a tuple of python classes (or implement a new one).  This system
allows, among many things, us to use frameworks that use derivation as part of their
public API.  Please see [classes-test](test/libpython_clj/classes_test.clj) for a documented
example of a simple pathway through the new API.  Note that if you use vanilla
`->py-fn` functions as part of the class definition you won't get access to the `self`
object.


#### Bugfixes

A general stability bugfix was made that was involved in the interoperation of
Clojure functions within Python.  Clojure functions weren't currently adding
a refcount to their return values.


## 1.16

Fixed a bug where the system would load multiple python libraries, not stopping
after the first valid library loaded.  There are two ways to control the system's
python library loading mechanism:

1. Pass in a library name in initialize!
2. alter-var-root the list of libraries in libpython-clj.jna.base before
   calling initialize!.


## 1.15

Moar syntax sugar --
```clojure
user> (py/$. numpy linspace)
<function linspace at 0x7fa6642766a8>
user> (py/$.. numpy random shuffle)
<built-in method shuffle of numpy.random.mtrand.RandomState object at 0x7fa66410cca8>
```


## 1.14

libpython-clj now searches for several shared libraries instead of being hardcoded
to just one of them.  Because of this, there is now:
```clojure
libpython-clj.jna.base/*python-library-names*
```

This is a sequence of library names that will be tried in order.

You can also pass in the desired library name as part of the initialize! call and
only this name will be tried.
