# libpython-clj

jna libpython bindings to the tech ecosystem.

If you have a production need right now, I strongly advise you to consider
[jep](https://github.com/ninia/jep).

This project is mean to explore what is possible using JNA.  This has a lot of
advantages over a [10,000 line C binding layer](https://github.com/ninia/jep/tree/master/src/main/c)
...if it works.


## Key Design Points


### Code Organization


There are 3 rough sections of code:
1. A JNA layer which is a 1-1 mapping most of the C API with no changes and full
   documentation.  The docstrings on the functions match the documentation if you lookup
   the 3.7 API documentation.  Users must manually manage the GIL when using this API
   layer.

2. An API implementation layer which knows about things like interpreters and type
   symbol tables.  Users must know how to manipulate the GIL and how the two garbage
   collectors work with each other to add to this layer.

3. A public API layer.  Details like managing the GIL or messing with garbage collection
   in general not leak out to this layer.



### Interpreters

Interpreters are global objects.  After initialize!, there is a main interpreter which
is used automatically from any thread.  Access to the interpreters relies on thread
local variables and interpreter locking so you can do things like:

```clojure
(with-interpreter interpreter
  some code)
```

This type of thing grabs the GIL if it hasn't already been claimed by the current thread
and off you go.  When the code is finished, it saves the interpreter thread state back
into a global atom and thus releases the GIL.  Interpreters have both shared and
per-interpreter state named `:shared-state` and `:interpreter-state` respectively.
Shared state would be the global type symbol table.  Interpreter state contains things
like a map of objects to their per-interpreter python bridging class.


### Garbage Collection

The system uses the tech.resource library to attach a GC hook to appropriate java object
that releases the associated python object if the java object goes out of scope.
Bridges use a similar technique to unregister the bridge on destruction of their python
counterpar.  There should be no need for manual addref/release calls in any user code.


### Copying Vs. Mirroring


Objects either in python or in java may be either copied or mirrored into the other
ecosystem.  Mirroring allows sharing complex and potentially changing datastructures
while copying allows a cleaner partitioning of concerns and frees both garbage
collection systems to act more independently.  Numeric buffers that have a direct
representation as a C-ptr (the datatype native-buffer type) have a zero-copy pathway
via numpy.



## Resources

* [libpython C api](https://docs.python.org/3.7/c-api/index.html#c-api-index)
* [spacy-cpp](https://github.com/d99kris/spacy-cpp)
* [base classes for python protocols](https://docs.python.org/3.7/library/collections.abc.html#collections-abstract-base-classes)

##

## Usage

```console
sudo apt install libpython3.6-dev
```

```clojure
(deftest print-test
  (libpy/Py_InitializeEx 0)
  (libpy/PyRun_SimpleString
"from time import time,ctime
print('Today is', ctime(time()))
")
  (let [finalize-val (libpy/Py_FinalizeEx)]
    (println finalize-val)))
```

```console
chrisn@chrisn-lt-2:~/dev/cnuernber/clj-libpython$ lein test
:tech.resource.gc Reference thread starting

lein test libpython-clj.jna-test
Library python3.7m found at [:system "python3.7m"]
Today is Thu May 16 14:26:21 2019
0

Ran 1 tests containing 0 assertions.
0 failures, 0 errors.
```

## License

Copyright © 2019 Chris Nuernberger

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
