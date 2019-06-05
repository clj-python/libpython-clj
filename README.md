# libpython-clj

JNA libpython bindings to the tech ecosystem.


* The exact same binary can run top of on multiple version of python reducing version
  dependency chain management issues.
* Development of new functionality is faster because it can be done from purely from the
  REPL.


## Usage


Python objects are essentially two dictionaries, one for 'attributes' and one for
'items'.  When you use python and use the '.' operator, you are referencing attributes.
If you use the '[]' operator, then you are referencing items.  Attributes are built in,
item access is optional and happens via the `__getitem__` and `__setitem__` attributes.
This is important to realize in that the code below doesn't look just like python we are
referencing the item and attribute systems by name and not via '.' or '[]'.


```console
sudo apt install libpython3.6-dev
pip3 install numpy
```


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
:tech.resource.gc Reference thread starting
nil

user> (initialize!)
info: executing python initialize!
Library python3.6m found at [:system "python3.6m"]
```

This dynamically finds the python shared library and loads it.  If you desire a
different shared library you can override
[here](https://github.com/cnuernber/libpython-clj/blob/142c0dbc7056d0f5cd1969548a127a119f641c86/src/libpython_clj/jna/base.clj#L12).


### Execute Some Python

`*out*` and `*err*` capture python stdout and stderr respectively.

```clojure
user> (run-simple-string "print('hey')")
hey
{:globals #object[com.sun.jna.Pointer 0x5d583373 "native@0x7ff0dc04f3a8"],
 :locals #object[com.sun.jna.Pointer 0x5d583373 "native@0x7ff0dc04f3a8"],
 :result #object[com.sun.jna.Pointer 0x86d7ae5 "native@0x7ff0d6a6c150"]}
```
The results are pure jna pointers.  Let's convert them to something a bit easier
to understand:

```clojure
user> (def bridged (->> *1
                        (map (fn [[k v]]
                               [k (as-jvm v)]))
                        (into {})))
#'user/bridged
(instance? java.util.Map (:globals bridged))
true
user> (:globals bridged)
{"__name__" "__main__", "__doc__" nil, "__package__" nil, "__loader__" #object[libpython_clj.python.bridge$generic_python_as_jvm$fn$reify__27727 0x4536b202 "libpython_clj.python.bridge$generic_python_as_jvm$fn$reify__27727@4536b202"], "__spec__" nil, "__annotations__" {}, "__builtins__" #object[libpython_clj.python.bridge$generic_python_as_jvm$fn$reify__27765 0x10a6eb20 "libpython_clj.python.bridge$generic_python_as_jvm$fn$reify__27765@10a6eb20"]}
```

We can get and set global variables here.  If we run another string, these are in the
environment.  The globals map itself is the global dict of the main module:

```console
(def main-globals (-> (add-module "__main__")
                            (module-dict)))
#'user/main-globals
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
(get (:globals bridged) "__name__")
"__main__"
user> (.put main-globals "my_var" 200)
nil
user> (run-simple-string "print('your variable is:' + str(my_var))")
your variable is:200
{:globals #object[com.sun.jna.Pointer 0x5cf3170a "native@0x7f89080573a8"],
 :locals #object[com.sun.jna.Pointer 0x5cf3170a "native@0x7f89080573a8"],
 :result #object[com.sun.jna.Pointer 0x59b7efc4 "native@0x7f8903d6f150"]}
```

Running python isn't ever really necessary, however, although it may at times be
convenient.  You can call attributes from clojure easily:

```clojure
user> (def np (import-module "numpy"))
#'user/np
user> (def ones-ary (call-attr np "ones" [2 3]))
#'user/ones-ary
user> (get-attr ones-ary "shape")
[2 3]
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

### Numpy

Speaking of numpy, you can move data between numpy and java easily.

```clojure
user> (def tens-data (as-tensor ones-ary))
#'user/tens-data
user> (println tens-data)
#tech.v2.tensor<float64>[2 3]
[[1.000 1.000 1.000]
 [1.000 1.000 1.000]]


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
{:globals #object[com.sun.jna.Pointer 0x515f9c6c "native@0x7f89080573a8"],
 :locals #object[com.sun.jna.Pointer 0x515f9c6c "native@0x7f89080573a8"],
 :result #object[com.sun.jna.Pointer 0x1610b946 "native@0x7f8903d6f150"]}
```

So heavy data has a zero-copy route.  Anything backed by a `:native-buffer` has a
zero copy pathway to and from numpy.  For more information on how this happens,
please refer to the datatype library [documentation](https://github.com/techascent/tech.datatype/tree/master/docs).

Just keep in mind, careless usage of zero copy is going to cause spooky action at a
distance.


## Further Information

* [design documentation](docs/design.md)
* [keras example](examples/keras-simple)


## Resources

* [libpython C api](https://docs.python.org/3.7/c-api/index.html#c-api-index)
* [create numpy from C ptr](https://stackoverflow.com/questions/23930671/how-to-create-n-dim-numpy-array-from-a-pointer)
* [create C ptr from numpy](https://docs.scipy.org/doc/numpy/reference/generated/numpy.ndarray.ctypes.html)



## License

Copyright © 2019 Chris Nuernberger

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
