# libpython-clj

JNA libpython bindings to the tech ecosystem.

If you have a production need right now, I strongly advise you to consider
[jep](https://github.com/ninia/jep).

This project is mean to explore what is possible using JNA.  This has a lot of
advantages over a static binding layer that must bind during an offline compilation
phase to specific version of the python runtime:

* The exact same binary can run top of on multiple version of python reducing version
  dependency chain management issues.
* Development of new functionality is faster because it can be done from purely from the
  REPL.


** This Project Is Rapidly Changing!!!**



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

You don't have to do this, however.  If you don't care what context you run in you can
just use the public API which under the covers simple does:


```clojure
(with-gil
  some code)
```

This type of thing grabs the GIL if it hasn't already been claimed by the current thread
and off you go.  When the code is finished, it saves the interpreter thread state back
into a global atom and thus releases the GIL.  Interpreters have both shared and
per-interpreter state named `:shared-state` and `:interpreter-state` respectively.
Shared state would be the global type symbol table.  Interpreter state contains things
like a map of objects to their per-interpreter python bridging class.

If the interpreter isn't specified it uses the main interpreter.  The simplest way to
ensure you have the gil is `with-gil`.  You may also use `(ensure-bound-interpreter)` if
you wish to penalize users for using a function incorrectly.  This returns the
interpreter currently bound to this thread or throws.


### Garbage Collection

The system uses the tech.resource library to attach a GC hook to appropriate java object
that releases the associated python object if the java object goes out of scope.
Bridges use a similar technique to unregister the bridge on destruction of their python
counterpart.  There should be no need for manual addref/release calls in any user code
aside from potentially (and damn rarely) a `(System/gc)` call.


### Copying Vs. Bridging


Objects either in python or in java may be either copied or mirrored into the other
ecosystem.  Mirroring allows sharing complex and potentially changing datastructures
while copying allows a cleaner partitioning of concerns and frees both garbage
collection systems to act more independently.  Numeric buffers that have a direct
representation as a C-ptr (the datatype native-buffer type) have a zero-copy pathway via
numpy.  If you want access to object functionality that object needs to be mirrored; so
for example if you want to call numpy functions then you need to mirror that object.
Tensors are always represented in python as numpy objects using zero-copy where possible
in all cases.


### Bridging


Python Callables implement `clojure.lang.IFn` along with a python specific interface so
in general you can call them like any other function but you also can use keyword
arguments if you know you are dealing with a python function.  Python dicts implement
`java.util.Map and clojure.lang.IFn` and lists are `java.util.List`,
java.util.RandomAcces`, and `clojure.lang.IFn`.  This allows fluid manipulation of
the datastructures (even mutation) from both languages.

You can create a python function from a clojure function with create-function.  You can
create a new bridge type by implementing the `libpython_clj.jna.JVMBridge` interface:

```java
public interface JVMBridge extends AutoCloseable
{
  public Pointer getAttr(String name);
  public void setAttr(String name, Pointer val);
  public String[] dir();
  public Object interpreter();
  public Object wrappedObject();
  // Called from python when the python mirror is deleted.
  // This had better not throw anything.
  public default void close() {}
}
```

If all you want to do is override the attribute map that is simple.  Here is the bridge
for java.util.Map:

```clojure
(defn jvm-map->python
  ^Pointer [^Map jvm-data]
  (with-gil
    (let [att-map
          {"__contains__" (jvm-fn->python #(.containsKey jvm-data %))
           "__eq__" (jvm-fn->python #(.equals jvm-data %))
           "__getitem__" (jvm-fn->python #(.get jvm-data %))
           "__setitem__" (jvm-fn->python #(.put jvm-data %1 %2))
           "__hash__" (jvm-fn->python #(.hashCode jvm-data))
           "__iter__" (jvm-fn->python #(.iterator ^Iterable jvm-data))
           "__len__" (jvm-fn->python #(.size jvm-data))
           "__str__" (jvm-fn->python #(.toString jvm-data))
           "clear" (jvm-fn->python #(.clear jvm-data))
           "keys" (jvm-fn->python #(seq (.keySet jvm-data)))
           "values" (jvm-fn->python #(seq (.values jvm-data)))
           "pop" (jvm-fn->python #(.remove jvm-data %))}]
      (create-bridge-from-att-map jvm-data att-map))))
```

### IO

`sys.stdout` and `sys.stderr` are redirected to `*out*` and `*err*` respectively.



## Resources

* [libpython C api](https://docs.python.org/3.7/c-api/index.html#c-api-index)
* [spacy-cpp](https://github.com/d99kris/spacy-cpp)
* [base classes for python protocols](https://docs.python.org/3.7/library/collections.abc.html#collections-abstract-base-classes)
* [create numpy from C ptr](https://stackoverflow.com/questions/23930671/how-to-create-n-dim-numpy-array-from-a-pointer)
* [create C ptr from numpy](https://docs.scipy.org/doc/numpy/reference/generated/numpy.ndarray.ctypes.html)


## Usage

```console
sudo apt install libpython3.6-dev
```

```clojure
user> (require '[libpython-clj.python :as python])
:tech.resource.gc Reference thread starting
nil
user> (python/initialize!)
info: executing python initialize!
Library python3.6m found at [:system "python3.6m"]
:ok

user> (python/run-simple-string "print('hey')")
hey
{:globals #object[com.sun.jna.Pointer 0x5d583373 "native@0x7ff0dc04f3a8"],
 :locals #object[com.sun.jna.Pointer 0x5d583373 "native@0x7ff0dc04f3a8"],
 :result #object[com.sun.jna.Pointer 0x86d7ae5 "native@0x7ff0d6a6c150"]}

user> (python/run-simple-string "print('syntax-errrrrr")
Execution error (ExceptionInfo) at libpython-clj.python.interpreter/check-error-throw (interpreter.clj:260).
  File "<string>", line 1
    print('syntax-errrrrr
                        ^
SyntaxError: EOL while scanning string literal


user> (python/run-simple-string "item = 10")
{:globals #object[com.sun.jna.Pointer 0x55418aaa "native@0x7ff0dc04f3a8"],
 :locals #object[com.sun.jna.Pointer 0x55418aaa "native@0x7ff0dc04f3a8"],
 :result #object[com.sun.jna.Pointer 0x52f3ccc9 "native@0x7ff0d6a6c150"]}

user> (def global-map (python/python->jvm (:globals *1)))
#'user/global-map
user> (keys global-map)
("__name__"
 "__doc__"
 "__package__"
 "__loader__"
 "__spec__"
 "__annotations__"
 "__builtins__"
 "item")
user> (get global-map "item")
10
user> (.put global-map "item" 100)
100

user> (python/run-simple-string "print('' + item)")
Execution error (ExceptionInfo) at libpython-clj.python.interpreter/check-error-throw (interpreter.clj:260).
Traceback (most recent call last):
  File "<string>", line 1, in <module>
TypeError: must be str, not int

user> (python/run-simple-string "print('' + str(item))")
100
{:globals #object[com.sun.jna.Pointer 0x52eee652 "native@0x7ff0dc04f3a8"],
 :locals #object[com.sun.jna.Pointer 0x52eee652 "native@0x7ff0dc04f3a8"],
 :result #object[com.sun.jna.Pointer 0x19e9d429 "native@0x7ff0d6a6c150"]}

;;  THIS SYNTAX IS CHANGING  !!!!;;
;;  The -> operator is going to be a copy operator while the
;;  as operator is going to be bridge, so  as-jvm instead of python->jvm

user> (def numpy (-> (python/import-module "numpy")
                     (python/python->jvm)))
#'user/numpy
user> (def ones-fn (get numpy "ones"))
#'user/ones-fn
user> (def ary-data (ones-fn [2 3]))
#'user/ary-data
user> (get ary-data "shape")
[2 3]

user> (def ary-ctype (get ary-data "ctypes"))
#'user/ary-ctype
user> (def test-ptr-val (get ary-ctype "data"))
#'user/test-ptr-val
user> test-ptr-val
140670943595536

user> (require '[tech.v2.datatype.jna :as dtype-jna])
nil
user> (def zero-copy-raw-data (dtype-jna/unsafe-address->typed-pointer test-ptr-val (* 8 6) :float64))
#'user/zero-copy-raw-data
user> zero-copy-raw-data
#object[java.nio.DirectDoubleBufferU 0x50beba84 "java.nio.DirectDoubleBufferU[pos=0 lim=6 cap=6]"]
user> (require '[tech.v2.datatype :as dtype])
nil
user> (dtype/->vector zero-copy-raw-data)
[1.0 1.0 1.0 1.0 1.0 1.0]
```

## License

Copyright © 2019 Chris Nuernberger

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
