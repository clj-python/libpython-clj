# LibPython-CLJ Usage


Python objects are essentially two dictionaries, one for 'attributes' and one for
'items'.  When you use python and use the '.' operator, you are referencing attributes.
If you use the '[]' operator, then you are referencing items.  Attributes are built in,
item access is optional and happens via the `__getitem__` and `__setitem__` attributes.
This is important to realize in that the code below doesn't look like python because we are
referencing the item and attribute systems by name and not via '.' or '[]'.

This would result in the following analogous code (full example [further on](#dataframe-access-full-example)):

```python
table.loc[row_date]
```

```clojure
(get-item (get-attr table :loc) row-date)
```

### Installation

#### Ubuntu

```console
sudo apt install libpython3.6
# numpy and pandas are required for unit tests.  Numpy is required for
# zero copy support.
python3.6 -m pip install numpy pandas --user
```

#### MacOSX

Python installation instructions [here](https://docs.python-guide.org/starting/install3/osx/).



### Initialize python
```clojure
user> (require '[libpython-clj2.python
                 :refer [as-python as-jvm
                         ->python ->jvm
                         get-attr call-attr call-attr-kw
                         get-item initialize!
                         run-simple-string
                         add-module module-dict
                         import-module
                         python-type
                         dir]
                 :as py])

nil

; Mac and Linux
user> (initialize!)
Jun 30, 2019 4:47:39 PM clojure.tools.logging$eval7369$fn__7372 invoke
INFO: executing python initialize!
Jun 30, 2019 4:47:39 PM clojure.tools.logging$eval7369$fn__7372 invoke
INFO: Library python3.6m found at [:system "python3.6m"]
Jun 30, 2019 4:47:39 PM clojure.tools.logging$eval7369$fn__7372 invoke
INFO: Reference thread starting
:ok

; Windows with Anaconda
(initialize! ; Python executable
             :python-executable "C:\\Users\\USER\\AppData\\Local\\Continuum\\anaconda3\\python.exe"
             ; Python Library
             :library-path "C:\\Users\\USER\\AppData\\Local\\Continuum\\anaconda3\\python37.dll"
             ; Anacondas PATH environment to load native dlls of modules (numpy, etc.)
             :windows-anaconda-activate-bat "C:\\Users\\USER\\AppData\\Local\\Continuum\\anaconda3\\Scripts\\activate.bat"
             )
...
:ok
```

This dynamically finds the python shared library and loads it using output from
the python3 executable on your system.  For information about how that works,
please checkout the code
[here](https://github.com/cnuernber/libpython-clj/blob/master/src/libpython_clj/python/interpreter.clj#L30).


### Execute Some Python

`*out*` and `*err*` capture python stdout and stderr respectively.

```clojure

user> (run-simple-string "print('hey')")
hey
{:globals
 {'__name__': '__main__', '__doc__': None, '__package__': None, '__loader__': <class '_frozen_importlib.BuiltinImporter'>, '__spec__': None, '__annotations__': {}, '__builtins__': <module 'builtins' (built-in)>},
 :locals
 {'__name__': '__main__', '__doc__': None, '__package__': None, '__loader__': <class '_frozen_importlib.BuiltinImporter'>, '__spec__': None, '__annotations__': {}, '__builtins__': <module 'builtins' (built-in)>}}
```

The results have been 'bridged' into java meaning they are still python objects but
there are java wrappers over the top of them.  For instance, `Object.toString` forwards
its implementation to the python function `__str__`.

```clojure
(def bridged (run-simple-string "print('hey')"))
(instance? java.util.Map (:globals bridged))
true
user> (:globals bridged)
{'__name__': '__main__', '__doc__': None, '__package__': None, '__loader__': <class '_frozen_importlib.BuiltinImporter'>, '__spec__': None, '__annotations__': {}, '__builtins__': <module 'builtins' (built-in)>}
```

We can get and set global variables here.  If we run another string, these are in the
environment.  The globals map itself is the global dict of the main module:

```clojure
(def main-globals (-> (add-module "__main__")
                            (module-dict)))
#'user/main-globals

user> main-globals
{'__name__': '__main__', '__doc__': None, '__package__': None, '__loader__': <class '_frozen_importlib.BuiltinImporter'>, '__spec__': None, '__annotations__': {}, '__builtins__': <module 'builtins' (built-in)>}
user> (keys main-globals)
("__name__"
 "__doc__"
 "__package__"
 "__loader__"
 "__spec__"
 "__annotations__"
 "__builtins__")
user> (get main-globals "__name__")
"__main__"
user> (.put main-globals "my_var" 200)
nil

user> (run-simple-string "print('your variable is:' + str(my_var))")
your variable is:200
{:globals
 {'__name__': '__main__', '__doc__': None, '__package__': None, '__loader__': <class '_frozen_importlib.BuiltinImporter'>, '__spec__': None, '__annotations__': {}, '__builtins__': <module 'builtins' (built-in)>, 'my_var': 200},
 :locals
 {'__name__': '__main__', '__doc__': None, '__package__': None, '__loader__': <class '_frozen_importlib.BuiltinImporter'>, '__spec__': None, '__annotations__': {}, '__builtins__': <module 'builtins' (built-in)>, 'my_var': 200}}
```

Running Python isn't ever really necessary, however, although it may at times be
convenient.  You can call attributes from clojure easily:

```clojure
user> (def np (import-module "numpy"))
#'user/np
user> (def ones-ary (call-attr np "ones" [2 3]))
#'user/ones-ary
user> ones-ary
[[1. 1. 1.]
 [1. 1. 1.]]
user> (call-attr ones-ary "__len__")
2
user> (vec ones-ary)
[[1. 1. 1.] [1. 1. 1.]]
user> (type (first *1))
:pyobject
user> (get-attr ones-ary "shape")
(2, 3)
user> (vec (get-attr ones-ary "shape"))
[2 3]

user> (dir ones-ary)
["T"
 "__abs__"
 "__add__"
 "__and__"
 "__array__"
 "__array_finalize__"
 ...
 ...
 "strides"
 "sum"
 "swapaxes"
 "take"
 "tobytes"
 "tofile"
 "tolist"
 "tostring"
 "trace"
 "transpose"
 "var"
 "view"]

### DataFrame access full example

Here's how to create Pandas DataFrame and accessing its rows via `loc` in both Python and Clojure:

```python
# Python
import numpy as np
import pandas as pan

dates = pan.date_range("1/1/2000", periods=8)
table = pan.DataFrame(np.random.randn(8, 4), index=dates, columns=["A", "B", "C", "D"])
row_date = pan.date_range(start="2000-01-01", end="2000-01-01")
table.loc[row_date]
```

```clojure
; Clojure
(require '[libpython-clj2.require :refer [require-python]])
(require-python '[numpy :as np])
(require-python '[pandas :as pan])

(def dates (pan/date_range "1/1/2000" :periods 8))
(def table (pan/DataFrame (call-attr np/random :randn 8 4) :index dates :columns ["A" "B" "C" "D"]))
(def row-date (pan/date_range :start "2000-01-01" :end "2000-01-01"))
(get-item (get-attr table :loc) row-date)
```

### Errors


Errors are caught and an exception is thrown.  The error text is saved verbatim
in the exception:


```clojure
user> (run-simple-string "print('syntax errrr")
Execution error (ExceptionInfo) at libpython-clj.python.interpreter/check-error-throw (interpreter.clj:260).
  File "<string>", line 1
    print('syntax errrr
                      ^
SyntaxError: EOL while scanning string literal
```

### Some Syntax Sugar

```clojure
user> (py/from-import numpy linspace)
#'user/linspace
user> (linspace 2 3 :num 10)
[2.         2.11111111 2.22222222 2.33333333 2.44444444 2.55555556
 2.66666667 2.77777778 2.88888889 3.        ]
user> (doc linspace)
-------------------------
user/linspace

    Return evenly spaced numbers over a specified interval.

    Returns `num` evenly spaced samples, calculated over the
    interval [`start`, `stop`].

```

* `from-import` - sugar around python `from a import b`.  Takes multiple b's.
* `import-as` - sugar around python `import a as b`.
* `$a` - call an attribute using symbol att name.  Keywords map to kwargs
* `$c` - call an object mapping keywords to kwargs


#### Experimental Sugar

We are trying to find the best way to handle attributes in order to shorten
generic python notebook-type usage.  The currently implemented direction is:

* `$.` - get an attribute.  Can pass in symbol, string, or keyword
* `$..` - get an attribute.  If more args are present, get the attribute on that
result.

```clojure
user> (py/$. numpy linspace)
<function linspace at 0x7fa6642766a8>
user> (py/$.. numpy random shuffle)
<built-in method shuffle of numpy.random.mtrand.RandomState object at 0x7fa66410cca8>
```

##### Extra sugar

`libpython-clj` offers syntactic forms similar to those offered by
Clojure for interacting with Python classes and objects.

**Class/object methods**
Where in Clojure you would use `(. obj method arg1 arg2 ... argN)`,
you can use `(py. pyobj method arg1 arg2 ... argN)`.

In Python, this is equivalent to `pyobj.method(arg1, arg2, ..., argN)`.
Concrete examples are shown below.

**Class/object attributes**
Where in Clojure you would use `(.- obj attr)`, you can use
`(py.- pyobj attr)`.

In Python, this is equivalent to `pyobj.attr`.
Concrete examples shown below.

**Nested attribute access**
To achieve a chain of method/attribute access, use the `py..` for.

```clojure
(py.. (requests/get "http://www.google.com")
      -content
      (decode "latin-1"))
```
(**Note**: requires Python `requests` module installled)

**Examples**

```clojure
user=> (require '[libpython-clj2.python :as py :refer [py. py.. py.-]])
nil
user=> (require '[libpython-clj2.require :refer [require-python]])

... debug info ...

user=> (require-python '[builtins :as python])
WARNING: AssertionError already refers to: class java.lang.AssertionError in namespace: builtins, being replaced by: #'builtins/AssertionError
WARNING: Exception already refers to: class java.lang.Exception in namespace: builtins, being replaced by: #'builtins/Exception
nil
user=> (def xs (python/list))
#'user/xs
user=> (py. xs append 1)
nil
user=> xs
[1]
user=> (py. xs extend [1 2 3])
nil
user=> xs
[1, 1, 2, 3]
user=> (py. xs __len__)
4
user=> ((py.- xs __len__)) ;; attribute syntax to get then call method
4
user=> (py. xs pop)
3
user=> (py. xs clear)
nil
;; requires Python requests module installed
user=> (require-python 'requests)
nil
user=> (def requests (py/import-module "requests"))
#'user/requests
user=> (py.. requests (get "http://www.google.com") -content (decode "latin-1"))
"<!doctype html><html itemscope=\"\" ... snip ... "
```



### Numpy

Speaking of numpy, you can move data between numpy and java easily.

```clojure
(require '[tech.v3.tensor :as dtt])
;;includes the appropriate protocols and multimethod overloads
(require '[libpython-clj2.python.np-array]
;;python objects created now for numpy arrays will be different.  So you have to require
;;np-array *before* you create your numpy data.
user> (def ones-ary (py/py. np ones [2 3]))
#'user/ones-ary
user> (def tens-data (dtt/as-tensor ones-ary))
#'user/tens-data
user> tens-data
#tech.v3.tensor<float64>[2 3]
[[1.000 1.000 1.000]
 [1.000 1.000 1.000]]


user> (require '[tech.v3.datatype :as dtype])
nil
;;Only constant-time count items can be copied, so vectors and arrays and such.
user> (def ignored (dtype/copy! (vec (repeat 6 5)) tens-data))
#'user/ignored
user> (.put main-globals "ones_ary" ones-ary)
nil

user> (run-simple-string "print(ones_ary)")
[[5. 5. 5.]
 [5. 5. 5.]]
{:globals
 {'__name__': '__main__', '__doc__': None, '__package__': None, '__loader__': <class '_frozen_importlib.BuiltinImporter'>, '__spec__': None, '__annotations__': {}, '__builtins__': <module 'builtins' (built-in)>, 'my_var': 200, 'ones_ary': array([[5., 5., 5.],
       [5., 5., 5.]])},
 :locals
 {'__name__': '__main__', '__doc__': None, '__package__': None, '__loader__': <class '_frozen_importlib.BuiltinImporter'>, '__spec__': None, '__annotations__': {}, '__builtins__': <module 'builtins' (built-in)>, 'my_var': 200, 'ones_ary': array([[5., 5., 5.],
       [5., 5., 5.]])}}
```

So heavy data has a zero-copy route.  Anything backed by a `:native-buffer` has a
zero copy pathway to and from numpy.  For more information on how this happens,
please refer to the datatype library [documentation](https://github.com/techascent/tech.datatype/tree/master/docs).

Just keep in mind, careless usage of zero copy is going to cause spooky action at a
    distance.


### Pickle

Speaking of numpy, you can pickle python objects and transform the result via numpy and dtype
to a java byte array and back:


```clojure
user> (require '[libpython-clj2.python :as py])
nil
user> (py/initialize!)
Sep 03, 2022 11:23:34 AM clojure.tools.logging$eval5948$fn__5951 invoke
INFO: Detecting startup info
Sep 03, 2022 11:23:34 AM clojure.tools.logging$eval5948$fn__5951 invoke
INFO: Startup info {:lib-version "3.9", :java-library-path-addendum "/home/chrisn/miniconda3/lib", :exec-prefix "/home/chrisn/miniconda3", :executable "/home/chrisn/miniconda3/bin/python3", :libnames ("python3.9m" "python3.9"), :prefix "/home/chrisn/miniconda3", :base-prefix "/home/chrisn/miniconda3", :libname "python3.9m", :base-exec-prefix "/home/chrisn/miniconda3", :python-home "/home/chrisn/miniconda3", :version [3 9 1], :platform "linux"}
Sep 03, 2022 11:23:34 AM clojure.tools.logging$eval5948$fn__5951 invoke
INFO: Prefixing java library path: /home/chrisn/miniconda3/lib
Sep 03, 2022 11:23:35 AM clojure.tools.logging$eval5948$fn__5951 invoke
INFO: Loading python library: python3.9
Sep 03, 2022 11:23:35 AM clojure.tools.logging$eval5948$fn__5951 invoke
INFO: Reference thread starting
:ok
user> (def data (py/->python {:a 1 :b 2}))
#'user/data
user> (def pickle (py/import-module "pickle"))
#'user/pickle
user> (def bdata (py/py. pickle dumps data))
#'user/bdata
user> bdata
b'\x80\x04\x95\x11\x00\x00\x00\x00\x00\x00\x00}\x94(\x8c\x01a\x94K\x01\x8c\x01b\x94K\x02u.'
user> (def np (py/import-module "numpy"))
#'user/np
user> (py/py. np frombuffer bdata :dtype "int8")
[-128    4 -107   17    0    0    0    0    0    0    0  125 -108   40
 -116    1   97 -108   75    1 -116    1   98 -108   75    2  117   46]
user> (require '[libpython-clj2.python.np-array])
nil
user> (def ary (py/py. np frombuffer bdata :dtype "int8"))
#'user/ary
user> (py/->jvm ary)
#tech.v3.tensor<int8>[28]
[-128 4 -107 17 0 0 0 0 0 0 0 125 -108 40 -116 1 97 -108 75 1 -116 1 98 -108 75 2 117 46]
user> (require '[tech.v3.datatype :as dt])
nil
user> (dt/->byte-array *2)
[-128, 4, -107, 17, 0, 0, 0, 0, 0, 0, 0, 125, -108, 40, -116, 1, 97, -108, 75, 1,
 -116, 1, 98, -108, 75, 2, 117, 46]
user> (require '[tech.v3.tensor :as dtt])
nil
user> (dtt/as-tensor *2)
nil
user> (def bdata *3)
#'user/bdata
user> bdata
[-128, 4, -107, 17, 0, 0, 0, 0, 0, 0, 0, 125, -108, 40, -116, 1, 97, -108, 75, 1,
 -116, 1, 98, -108, 75, 2, 117, 46]
user> (type bdata)
[B
user> (def tens (dtt/reshape bdata [(dt/ecount bdata)]))
#'user/tens
user> (def pdata (py/->python tens))
#'user/pdata
user> pdata
[-128    4 -107   17    0    0    0    0    0    0    0  125 -108   40
 -116    1   97 -108   75    1 -116    1   98 -108   75    2  117   46]
user> (py/python-type *1)
:ndarray
user> (def py-ary *2)
#'user/py-ary
user> (def py-bytes (py/py. py-ary tobytes))
#'user/py-bytes
user> (py/py. pickle loads py-bytes)
{'a': 1, 'b': 2}
user>
```
