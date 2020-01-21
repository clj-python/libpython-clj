# Time for a ChangeLog!

## 1.32

* Python executables can now be speficied directly using the syntax
  ```clojure 
  (py/initialize! :python-executable <executable>)
  ```
  where **<executable>** can be a system installation of Python
  such as `"python3"`, `"python3.7"`; it can also be a fully qualified
  path such as `"/usr/bin/python3.7"`; or any Python executable along
  your discoverable system path.
  
* Python virtual environments can now be used instead of system 
  installations! This has been tested on Linux/Ubuntu variants
  with virtual environments installed with 
  ```bash 
  virtualenv -p $(which <python-version>) env
  ```
  and then invoked using 
  ```clojure 
  (py/initialize! :python-executable "/abs/path/to/env/bin/python")
  ```
  
  Tested on Python 3.6.8 and Python 3.7.
  
* **breaking change** (and remediation): `require-python` no longer
  automatically binds the Python module to the Clojure the namespace 
  symbol.  If you wish to bind the module to the namespace symbol,
  you need to use the `:bind-ns` flag.  Example:
  
  ```clojure 
  (require-python 'requests) ;;=> nil
  requests ;;=> throws Exception
  
  (require-python '[requests :bind-ns]) ;;=> nil
  (py.. requests 
        (get "https://www.google.com)
        -content 
        (decode "latin-1)) ;; works
```
  


## 1.31

* Python objects are now datafy-able and nav-igable.  `require-python`
  is now rebuilt using datafy.
  
* `py.`, `py.-`, and `py..` added to the `libpython-clj` APIs
  to allow method/attribute access more consistent with idiomatic
  Clojure forms.
  

## 1.30

This release is a big one.  With finalizing `require-python` we have a clear way
to use Python in daily use and make it look good in normal Clojure usage.  There
is a demo of [facial recognition](https://github.com/cnuernber/facial-rec) using some
of the best open systems for doing this; this demo would absolutely not be possible
without this library due to the extensive use of numpy and cython to implement the
face detection.  We can now interact with even very complex Python systems with
roughly the same performance as a pure Python system.

#### Finalized `require-python`

Lots of work put in to make the `require-python` pathway work with
classes and some serious refactoring overall.

#### Better Numpy Support

* Most of the datatype libraries math operators supported by numpy objects (+,-,etc).
* Numpy objects can be used in datatype library functions (like `copy`, `make-container`)
  and work in optimized ways.
  
```clojure
libpython-clj.python.numpy-test> (def test-ary (py/$a np-mod array (->> (range 9)
                                                                        (partition 3)
                                                                        (mapv vec))))
#'libpython-clj.python.numpy-test/test-ary
libpython-clj.python.numpy-test> test-ary
[[0 1 2]
 [3 4 5]
 [6 7 8]]
libpython-clj.python.numpy-test> (dfn/+ test-ary 2)
[[ 2  3  4]
 [ 5  6  7]
 [ 8  9 10]]
libpython-clj.python.numpy-test> (dfn/> test-ary 4)
[[False False False]
 [False False  True]
 [ True  True  True]]
```
  
#### Bugs Fixed
* Support for java character <-> py string
* Fixed potential crash related to use of delay mechanism and stack based gc.
* Added logging to complain loudly if refcounts appear to be bad.


## 1.29

* Found/fixed issue with `->jvm` and large Python dictionaries.


## 1.28

* `(range 5)` - Clojure ranges <-> Python ranges when possible.
* bridged types derive from `collections.abc.*` so that they pass instance checks in
  libraries that are checking for generic types.
* Really interesting unit test for
  [generators, ranges and sequences](test/libpython_clj/iter_gen_seq_test.clj).


## 1.27

* Fixed bug where `(as-python {:is_train false})` results in a dictionary with a none
  value instead of a false value.  This was found through hours of debugging why
  mxnet's forward function call was returning different values in Clojure than in
  Python.


## 1.26

* [python startup work](https://github.com/cnuernber/libpython-clj/commit/16da3d885f29bde59ea219c9438b9d3654387971)
* [python generators & clojure transducers](https://github.com/cnuernber/libpython-clj/pull/27)
* [requre-python reload fix](https://github.com/cnuernber/libpython-clj/pull/24)
* Bugfix with `require-python` :reload semantics.


## 1.25

Fixed (with tests) major issue with `require-python`.


## 1.24

Clojure's range is now respected in two different ways:
* `(range)` - bridges to a Python iterable
* `(range 5)` - copies to a Python list


## 1.23

Equals, hashcode, nice default `.toString` of Python types:

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

Working to make more Python environments work out of the box.  Currently have a
testcase for conda working in a clean install of a docker container.  There is now a
new method: `libpython-clj.python.interpreter/detect-startup-info` that attempts
call `python3-config --prefix` and `python3 --version` in order to automagically
configure the Python library.


## 1.21

Bugfix release.  Passing infinite sequences to Python functions was
causing a hang as libpython-clj attempted to copy the sequence.  The
current calling convention does a shallow copy of things that are list-like
or map-like, while bridging things that are iterable or don't fall into
the above categories.

This exposed a bug that caused reference counting to be subtly wrong when
Python iterated through a bridged object.  And that was my life for a day.

## 1.20

With too many huge things we had to skip a few versions!

#### require-python

`require-python` works like require but it works on Python modules.
`require-python` dynamically loads the module and exports it's symbols into
a Clojure namespace.  There are many options available for this pathway.


This implements a big step towards embedding Python in Clojure in a simple,
clear, and easy to use way.  One important thing to consider is the require
has a `:reload:` option to allow you to actively develop a Python module and
test it via Clojure.


This excellent work was in large part done by [James Tolton](https://github.com/jjtolton).


* [require-python-test](test/libpython_clj/require_python_test.clj)


#### Clojure-defined Python classes

You can now extend a tuple of Python classes (or implement a new one).  This system
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

Fixed a bug where the system would load multiple Python libraries, not stopping
after the first valid library loaded.  There are two ways to control the system's
Python library loading mechanism:

1. Pass in a library name in `initialize!`
2. `alter-var-root` the list of libraries in `libpython-clj.jna.base` before
   calling `initialize!`.


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

You can also pass in the desired library name as part of the `initialize!` call and
only this name will be tried.
