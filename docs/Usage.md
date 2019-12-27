# Usage


Python objects are essentially two dictionaries, one for 'attributes' and one for
'items'.  When you use python and use the '.' operator, you are referencing attributes.
If you use the '[]' operator, then you are referencing items.  Attributes are built in,
item access is optional and happens via the `__getitem__` and `__setitem__` attributes.
This is important to realize in that the code below doesn't look like python because we are
referencing the item and attribute systems by name and not via '.' or '[]'.


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
user>

user> (require '[libpython-clj.python
                 :refer [as-python as-jvm
                         ->python ->jvm
                         get-attr call-attr call-attr-kw
                         get-item att-type-map
                         call call-kw initialize!
                         as-numpy as-tensor ->numpy
                         run-simple-string
                         add-module module-dict
                         import-module
                         python-type]])
nil


user> (initialize!)
Jun 30, 2019 4:47:39 PM clojure.tools.logging$eval7369$fn__7372 invoke
INFO: executing python initialize!
Jun 30, 2019 4:47:39 PM clojure.tools.logging$eval7369$fn__7372 invoke
INFO: Library python3.6m found at [:system "python3.6m"]
Jun 30, 2019 4:47:39 PM clojure.tools.logging$eval7369$fn__7372 invoke
INFO: Reference thread starting
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

Running python isn't ever really necessary, however, although it may at times be
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

user> (att-type-map ones-ary)
{"T" :ndarray,
 "__abs__" :method-wrapper,
 "__add__" :method-wrapper,
 "__and__" :method-wrapper,
 "__array__" :builtin-function-or-method,
 "__array_finalize__" :none-type,
 "__array_function__" :builtin-function-or-method,
 "__array_interface__" :dict,
 "__array_prepare__" :builtin-function-or-method,
 "__array_priority__" :float,
 "__array_struct__" :py-capsule,
 "__array_ufunc__" :builtin-function-or-method,
 "__array_wrap__" :builtin-function-or-method,
 "__bool__" :method-wrapper,
 "__class__" :type,
 "__complex__" :builtin-function-or-method,
 "__contains__" :method-wrapper,
 ...
 "std" :builtin-function-or-method,
 "strides" :tuple,
 "sum" :builtin-function-or-method,
 "swapaxes" :builtin-function-or-method,
 "take" :builtin-function-or-method,
 "tobytes" :builtin-function-or-method,
 "tofile" :builtin-function-or-method,
 "tolist" :builtin-function-or-method,
 "tostring" :builtin-function-or-method,
 "trace" :builtin-function-or-method,
 "transpose" :builtin-function-or-method,
 "var" :builtin-function-or-method,
 "view" :builtin-function-or-method}
```


### att-type-map

It can be extremely helpful to print out the attribute name->attribute type map:

```clojure
user> (att-type-map ones-ary)
{"T" :ndarray,
 "__abs__" :method-wrapper,
 "__add__" :method-wrapper,
 "__and__" :method-wrapper,
 "__array__" :builtin-function-or-method,
 "__array_finalize__" :none-type,
 "__array_function__" :builtin-function-or-method,
 "__array_interface__" :dict,
 ...
  "real" :ndarray,
 "repeat" :builtin-function-or-method,
 "reshape" :builtin-function-or-method,
 "resize" :builtin-function-or-method,
 "round" :builtin-function-or-method,
 "searchsorted" :builtin-function-or-method,
 "setfield" :builtin-function-or-method,
 "setflags" :builtin-function-or-method,
 "shape" :tuple,
 "size" :int,
 "sort" :builtin-function-or-method,
 ...
}
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
* `import-as` - surgar around python `import a as b`.
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


### Numpy

Speaking of numpy, you can move data between numpy and java easily.

```clojure
user> (def tens-data (as-tensor ones-ary))
#'user/tens-data
user> (println tens-data)
#tech.v2.tensor<float64>[2 3]
[[1.000 1.000 1.000]
 [1.000 1.000 1.000]]
nil


user> (require '[tech.v2.datatype :as dtype])
nil
user> (def ignored (dtype/copy! (repeat 6 5) tens-data))
#'user/ignored
user> (.put main-globals "ones_ary" ones_ary)
Syntax error compiling at (*cider-repl cnuernber/libpython-clj:localhost:39019(clj)*:191:7).
Unable to resolve symbol: ones_ary in this context
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
