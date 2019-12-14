# Time for a ChangeLog!


## 1.26-SNAPSHOT



* [python startup work](https://github.com/cnuernber/libpython-clj/commit/16da3d885f29bde59ea219c9438b9d3654387971)
* [python generates & clojure transducers](https://github.com/cnuernber/libpython-clj/pull/27) 



## 1.25


Fixed (with tests) major issue with require-python.


## 1.24


Clojure's range is now respected in two different ways:
* `(range)` - bridges to a python iterable
* `(range 5)` - copies to a python list


## 1.23


Equals, hashcode, nice default .toString of python types:

```clojure
user> (require '[libpython-clj.python :as py])
nil
user> (def test-tuple (py/->py-tuple [1 2]))
#'user/test-tuple
user> (require '[libpython-clj.require :refer [require-python]])
nil
user> (require-python '[builtins :as bt])
nil
user> (bt/type test-tuple)
builtins.tuple
user> test-tuple
(1, 2)
user> (def new-tuple (py/->py-tuple [3 4]))
#'user/new-tuple
user> (= test-tuple new-tuple)
false
user> (= test-tuple (py/->py-tuple [1 2]))
true
user> (.hashCode test-tuple)
2130570162
user> (.hashCode (py/->py-tuple [1 2]))
2130570162
user> (require-python '[numpy :as np])
nil
user> (def np-ary (np/array [1 2 3]))
#'user/np-ary
user> np-ary
[1 2 3]
user> (bt/type np-ary)
numpy.ndarray
user> (py/python-type *1)
:type
```


## 1.22

Working to make more python environments work out of the box.  Currently have a
testcase for conda working in a clean install of a docker container.  There is now a
new method: `libpython-clj.python.interpreter/detect-startup-info` that attempts
call `python3-config --prefix` and `python3 --version` in order to automagically
configure the python library.


## 1.21

Bugfix release.  Passing infinite sequences to python functions was
causing a hang as libpython-clj attempted to copy the sequence.  The
current calling convention does a shallow copy of things that are list-like
or map-like, while bridging things that are iterable or don't fall into
the above categories.

This exposed a bug that caused reference counting to be subtly wrong when
python iterated through a bridged object.  And that was my life for a day.

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
