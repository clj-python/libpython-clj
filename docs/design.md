# LibPython-CLJ Design Notes


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
   in general do not leak out to this layer.



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

The system uses the [tech.resource](https://github.com/techascent/tech.resource) 
library to attach a GC hook to appropriate java object
that releases the associated python object if the java object goes out of scope.
Bridges use a similar technique to unregister the bridge on destruction of their python
counterpart.  There should be no need for manual addref/release calls in any user code
aside from potentially (and damn rarely) a `(System/gc)` call.


### Copying Vs. Bridging


Objects either in python or in java may be either copied or bridged into the other
ecosystem.  Bridging allows sharing complex and potentially changing datastructures
while copying allows a cleaner partitioning of concerns and frees both garbage
collection systems to act more independently.  Numeric buffers that have a direct
representation as a C-ptr (the datatype native-buffer type) have a zero-copy pathway via
numpy.  If you want access to object functionality that object needs to be bridged; so
for example if you want to call numpy functions then you need to bridge that object.
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
This rerouting is done using a bridge that is then set as `sys.stdout` or `sys.stderr`:

```clojure
(defn create-var-writer
  "Returns an unregistered bridge"
  ^Pointer [writer-var]
  (with-gil
    (create-bridge-from-att-map
     writer-var
     {"write" (->python (fn [& args]
                          (.write ^Writer @writer-var (str (first args)))))
      "flush" (->python (fn [& args]))
      "isatty" (->python (fn [& args]
                           (libpy/Py_False)))
      })))

(defn setup-std-writer
  [writer-var sys-mod-attname]
  (with-gil
    (let [sys-module (import-module "sys")
          std-out-writer (get-or-create-var-writer writer-var)]
      (py-proto/set-attr! sys-module sys-mod-attname std-out-writer)
      :ok)))


(defn initialize!
  [& {:keys [program-name no-io-redirect?]}]
  (when-not @pyinterp/*main-interpreter*
    (pyinterp/initialize! program-name)
    ;;setup bridge mechansim and io redirection
    (pyinterop/register-bridge-type!)
    (when-not no-io-redirect?
      (pyinterop/setup-std-writer #'*err* "stderr")
      (pyinterop/setup-std-writer #'*out* "stdout")))
  :ok)
```
