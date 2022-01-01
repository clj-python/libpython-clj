(ns libpython-clj2.java-api
  "A java api is exposed for libpython-clj2.  The methods below are statically callable
  without the leading '-'.  Note that returned python objects implement the respective
  java interfaces so a python dict will implement java.util.Map, etc.  There is some
  startup time as Clojure dynamically compiles the source code but this binding should
  have great runtime characteristics in comparison to any other java python engine.


  Note that you can pass java objects into python.  An implementation of java.util.Map will
  appear to python as a dict-like object an implementation of java.util.List will look
  like a sequence and if the thing is iterable then it will be iterable in python.
  To receive callbacks from python you can provide an implementation of the
  interface `clojure.lang.IFn` - see `clojure.lang.AFn` for a base class that makes this
  easier.


  Performance:

  * `fastcall` - For the use case of repeatedly calling a single function, for instance
  for each row of a table or in a tight loop, use `allocateFastcallContext` before the loop,
  use fastcall to call your function noting that it takes an additional context parameter
  the user can pass in as the first argument, and use `releaseFastcallContext` afterwords.  This
  pathway eliminates nearly all of the overhead of calling python from Java and uses the
  absolutely minimal number of C api calls in order to call a python function.  This is
  about twice as fast as using the generic architecture to call a python function.

  * `makeFastcallable` - A simplified version of fastcall to be used with try-with-resources.
  This is a safer but equally-as-fast pathway as `fastcall`.

  * HAIR ON FIRE MODE - If you are certain you are correctly calling lockGIL and unlockGIL
  then you can define a variable, `-Dlibpython_clj.manual_gil=true` that will disable
  automatic GIL lock/unlock system and correctness checking.  This is useful, for instance,
  if you are going to lock libpython-clj to a thread and control all access to it yourself.
  This pathway will get at most 10% above using fastcall by itself.


```java
  java_api.initialize(null);
  np = java_api.importModule(\"numpy\");
  Object ones = java_api.getAttr(np, \"ones\");
  ArrayList dims = new ArrayList();
  dims.add(2);
  dims.add(3);
  Object npArray = java_api.call(ones, dims); //see fastcall notes above
```"
  (:import [java.util Map Map$Entry List]
           [java.util.function Supplier]
           [clojure.java.api Clojure])
  (:gen-class
   :name libpython_clj2.java_api
   :main false
   :methods [#^{:static true} [initialize [java.util.Map] Object]
             #^{:static true} [runString [String] java.util.Map]
             #^{:static true} [lockGIL [] int]
             #^{:static true} [unlockGIL [int] Object]
             #^{:static true} [inPyContext [java.util.function.Supplier] Object]
             #^{:static true} [hasAttr [Object String] Boolean]
             #^{:static true} [getAttr [Object String] Object]
             #^{:static true} [setAttr [Object String Object] Object]
             #^{:static true} [hasItem [Object Object] Boolean]
             #^{:static true} [getItem [Object Object] Object]
             #^{:static true} [setItem [Object Object Object] Object]
             #^{:static true} [importModule [String] Object]
             #^{:static true} [callKw [Object java.util.List java.util.Map] Object]
             #^{:static true} [callPos [Object java.util.List] Object]
             #^{:static true} [call [Object] Object]
             #^{:static true} [call [Object Object] Object]
             #^{:static true} [call [Object Object Object] Object]
             #^{:static true} [call [Object Object Object Object] Object]
             #^{:static true} [call [Object Object Object Object Object] Object]
             #^{:static true} [call [Object Object Object Object Object Object] Object]
             #^{:static true} [call [Object Object Object Object Object Object Object] Object]
             #^{:static true} [allocateFastcallContext [] Object]
             #^{:static true} [releaseFastcallContext [Object] Object]
             ;;args require context
             #^{:static true} [fastcall [Object Object] Object]
             #^{:static true} [fastcall [Object Object Object] Object] ;;1
             #^{:static true} [fastcall [Object Object Object Object] Object] ;;2
             #^{:static true} [fastcall [Object Object Object Object Object] Object] ;;3
             #^{:static true} [fastcall [Object Object Object Object Object Object] Object] ;;4
             #^{:static true} [makeFastcallable [Object] java.lang.AutoCloseable]
             #^{:static true} [copyToPy [Object] Object]
             #^{:static true} [copyToJVM [Object] Object]
             #^{:static true} [createArray [String Object Object] Object]
             #^{:static true} [arrayToJVM [Object] java.util.Map]]))


(set! *warn-on-reflection* true)


(def requires* (delay
                 (let [require (Clojure/var "clojure.core" "require")]
                   (doseq [clj-ns ["tech.v3.datatype"
                                   "tech.v3.tensor"
                                   "libpython-clj2.python"
                                   "libpython-clj2.python.fn"
                                   "libpython-clj2.python.ffi"
                                   "libpython-clj2.python.gc"
                                   "libpython-clj2.python.np-array"]]
                     (require (Clojure/read clj-ns))))))


(def fastcall* (delay (Clojure/var "libpython-clj2.python.fn" "fastcall")))
(def allocate-fastcall-context* (delay (Clojure/var "libpython-clj2.python.fn" "allocate-fastcall-context")))
(def release-fastcall-context* (delay (Clojure/var "libpython-clj2.python.fn" "release-fastcall-context")))
(def make-fastcallable* (delay (Clojure/var "libpython-clj2.python.fn" "make-fastcallable")))


(defn -initialize
  "See options for [[libpython-clj2.python/initialize!]].  Note that the keyword
  option arguments can be provided by using strings so `:library-path` becomes
  the key \"library-path\".  Also note that there is still a GIL underlying
  all of the further operations so java should access python via single-threaded
  pathways."
  [options]
  @requires*
  (apply (Clojure/var "libpython-clj2.python" "initialize!")
         (->> options
              (mapcat (fn [^Map$Entry entry]
                        [(keyword (.getKey entry)) (.getValue entry)])))))


(defn -lockGIL
  "Attempt to lock the gil.  This is safe to call in a reentrant manner.
  Returns an integer representing the gil state that must be passed into unlockGIL.

  See documentation for [pyGILState_Ensure](https://docs.python.org/3/c-api/init.html#c.PyGILState_Ensure).

  Note that the API will do this for you but locking
  the GIL before doing a string of operations is faster than having each operation lock
  the GIL individually."
  []
  (int ((Clojure/var "libpython-clj2.python.ffi" "lock-gil"))))


(defn -unlockGIL
  "Unlock the gil passing in the gilstate returned from lockGIL.  Each call to lockGIL must
  be paired to a call to unlockGIL."
  [gilstate]
  ((Clojure/var "libpython-clj2.python.ffi" "unlock-gil") gilstate))


(defn -runString
  "Run a string returning a map of two keys -\"globals\" and \"locals\" which each point to
  a java.util.Map of the respective contexts.  See documentation under
  [[libpython-clj2.python/run-simple-string]] - specifically this will never return
  the result of the last statement ran - you need to set a value in the global or local
  context."
  ^java.util.Map [^String code]
  (->> ((Clojure/var "libpython-clj2.python" "run-simple-string") code)
       (map (fn [[k v]]
              [(name k) v]))
       (into {})))


(defn -inPyContext
  "Run some code in a python context where all python objects allocated within the context
  will be deallocated once the context is released.  The code must not return references
  to live python objects."
  [^Supplier fn]
  ((Clojure/var "libpython-clj2.python" "in-py-ctx") fn))


(defn -hasAttr
  "Returns true if this python object has this attribute."
  [pyobj attname]
  (boolean ((Clojure/var "libpython-clj2.python" "has-attr?") pyobj attname)))


(defn -getAttr
  "Get a python attribute.  This corresponds to the python '.' operator."
  [pyobj attname]
  ((Clojure/var "libpython-clj2.python" "get-attr") pyobj attname))


(defn -setAttr
  "Set an attribute on a python object.  This corresponds to the `__setattr` python call."
  [pyobj attname objval]
  ((Clojure/var "libpython-clj2.python" "set-attr!") pyobj attname objval))


(defn -hasItem
  "Return true if this pyobj has this item"
  [pyobj itemName]
  (boolean ((Clojure/var "libpython-clj2.python" "has-item?") pyobj itemName)))


(defn -getItem
  [pyobj itemName]
  ((Clojure/var "libpython-clj2.python" "get-item") pyobj itemName))


(defn -setItem
  [pyobj itemName itemVal]
  ((Clojure/var "libpython-clj2.python" "set-item!") pyobj itemName itemVal))


(defn -importModule
  "Import a python module.  Module entries can be accessed via `getAttr`."
  [modname]
  ((Clojure/var "libpython-clj2.python" "import-module") modname))


(defn -callKw
  "Call a python callable with keyword arguments.  Note that you don't need this pathway
  to call python methods if you do not need keyword arguments; if the python object is
  callable then it will implement `clojure.lang.IFn` and you can use `invoke`."
  [pyobj pos-args kw-args]
  (let [gstate (-lockGIL)]
    (try
      ((Clojure/var "libpython-clj2.python.fn" "call-kw")
       pyobj pos-args (->> kw-args
                           (map (fn [^Map$Entry entry]
                                  [(.getKey entry)
                                   (.getValue entry)]))
                           (into {})))
      (finally
        (-unlockGIL gstate)))))


(defn -callPos
  "Call a python callable with only positional arguments.  Note that you don't need this pathway
  to call python methods - you can also cast the python object to `clojure.lang.IFn` and call
  `invoke` directly."
  [pyobj pos-args]
  (apply pyobj pos-args))


(defn -call
  "Call a clojure `IFn` object.  Python callables implement this interface so this works for
  python objects.  This is a convenience method around casting implementation of
  `clojure.lang.IFn` and calling `invoke` directly."
  ([item]
   (item))
  ([item arg1]
   (item arg1))
  ([item arg1 arg2]
   (item arg1 arg2))
  ([item arg1 arg2 arg3]
   (item arg1 arg2 arg3))
  ([item arg1 arg2 arg3 arg4]
   (item arg1 arg2 arg3 arg4))
  ([item arg1 arg2 arg3 arg4 arg5]
   (item item arg1 arg2 arg3 arg4 arg5))
  ([item arg1 arg2 arg3 arg4 arg5 arg6]
   (item item arg1 arg2 arg3 arg4 arg5 arg6)))


(defn -allocateFastcallContext
  "Allocate a fastcall context.  See docs for [[-fastcall]].  The returned context must be
  release via [[-releaseFastcallContext]]."
  []
  (@allocate-fastcall-context*))


(defn -releaseFastcallContext
  "Release a fastcall context.  See docs for [[-fastcall]].  This is safe to call with
  null and to call multiple times on the same context."
  [ctx]
  (when ctx
    (@release-fastcall-context* ctx)))


(defn -fastcall
  "Call a python function as fast as possible using a fixed number of positional arguments.
  The caller must provide an allocated fastcall context
  via [[-allocateFastcallContext]] and furthermore the caller may choose to deallocate the
  fastcall context with [[-releaseFastcallContext]].

  Current overloads support arities up to 4 arguments.  You must not use the same context
  with different arities - contexts are allocated in an arity-specific manner.

  For a more condensed version see [[makeFastcallable]]."
  ([ctx item]
   ;;ctx is unused but placed here to allow rapid search/replace to be correct.
   (@fastcall* item))
  ([ctx item arg1]
   (@fastcall* ctx item arg1))
  ([ctx item arg1 arg2]
   (@fastcall* ctx item arg1 arg2))
  ([ctx item arg1 arg2 arg3]
   (@fastcall* ctx item arg1 arg2 arg3))
  ([ctx item arg1 arg2 arg3 arg4]
   (@fastcall* ctx item arg1 arg2 arg3 arg4)))


(defn -makeFastcallable
  "Given a normal python callable, make a fastcallable object that needs to be closed.
  This should be seen as a callsite optimization for repeatedly calling a specific python
  function in a tight loop with positional arguments.  It is not intended to be used in a
  context where you will then pass this object around as this is not a reentrant optimization.

```java
  try (AutoCloseable fastcaller = java_api.makeFastcallable(pycallable)) {
     tightloop {
       java_api.call(fastcaller, arg1, arg2);
     }
  }
```"
  ^java.lang.AutoCloseable [item]
  (@make-fastcallable* item))


(defn -copyToPy
  "Copy a basic jvm object, such as an implementation of java.util.Map or java.util.List to
  a python object."
  [object]
  ((Clojure/var "libpython-clj2.python" "->python") object))


(defn -copyToJVM
  "Copy a python object such as a dict or a list into a comparable JVM object."
  [object]
  ((Clojure/var "libpython-clj2.python" "->jvm") object))


(defn -createArray
  "Create a numpy array from a tuple of string datatype, shape and data.
  * `datatype` - One of \"int8\" \"uint8\" \"int16\" \"uint16\" \"int32\" \"uint32\"
  \"int64\" \"uint64\" \"float32\" \"float64\".
  * `shape` - integer array of shapes.
  * `data` - list or array of data.  This will of course be fastest if the datatype
  of the array matches the datatype."
  [datatype shape data]
  (let [reshape (Clojure/var "tech.v3.tensor" "reshape")
        ->python (Clojure/var "libpython-clj2" "->python")]
    (-> ((Clojure/var "tech.v3.tensor" "->tensor") data :datatype (keyword datatype))
        (reshape shape)
        (->python))))


(defn -arrayToJVM
  "Copy (efficiently) a numeric numpy array into a jvm map containing keys \"datatype\",
  \"shape\", and \"data\"."
  [pyobj]
  (let [tens-data ((Clojure/var "libpython-clj2.python" "as-jvm") pyobj)]
    {"datatype" (name ((Clojure/var "tech.v3.datatype" "elemwise-datatype") tens-data))
     "shape" (int-array ((Clojure/var "tech.v3.datatype" "shape") tens-data))
     "data" ((Clojure/var "tech.v3.datatype" "->array") tens-data)}))
