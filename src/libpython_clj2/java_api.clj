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
  interface [clojure.lang.IFn](https://clojure.github.io/clojure/javadoc/clojure/lang/IFn.html)
  - see [clojure.lang.AFn](https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/AFn.java) for a base class that makes this easier.


  Performance:

  There are a two logical ways to repeatedly invoking python functionality.  The first is
  to set some globals and repeatedly [[runStringAsInput]] a script.  The second is
  'exec'ing a script, finding an exported function in the global dict and calling
  that.  With libpython-clj, the second pathway -- repeatedly calling a function --
  is going to be faster than the first if the user makes a fastcallable out of the function
  to be invoked.  Here are some sample timings for an extremely simple function with two
  arguments:


```console
Python fn calls/ms 1923.2490094806021
Python fastcallable calls/ms 3776.767751742239
Python eval pathway calls/ms 2646.0478013509883
```


  * [[makeFastcallable]] - - For the use case of repeatedly calling a single function - this
  will cache the argument tuple for repeated use as opposed to allocating the argument tuple
  every call.  This can be a surprising amount faster -- 2x-3x -- than directly calling the
  python callable.  Once a fastcallable object is made you can either cast it to a
  [clojure.lang.IFn](https://clojure.github.io/clojure/javadoc/clojure/lang/IFn.html)
  or call it via the provided `call` static method.


  * HAIR ON FIRE MODE - If you are certain you are correctly calling lockGIL and unlockGIL
  then you can define a variable, `-Dlibpython_clj.manual_gil=true` that will disable
  automatic GIL lock/unlock system and gil correctness checking.  This is useful, for instance,
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
  (:import [java.util Map Map$Entry List HashMap LinkedHashMap Set HashSet]
           [java.util.function Supplier]
           [clojure.java.api Clojure]
           [com.google.common.cache CacheBuilder RemovalListener])
  (:gen-class
   :name libpython_clj2.java_api
   :main false
   :methods [#^{:static true} [initialize [java.util.Map] Object]
             #^{:static true} [initializeEmbedded [] Object]
             #^{:static true} [lockGIL [] long]
             #^{:static true} [unlockGIL [long] Object]
             #^{:static true} [hasAttr [Object String] Boolean]
             #^{:static true} [getAttr [Object String] Object]
             #^{:static true} [setAttr [Object String Object] Object]
             #^{:static true} [hasItem [Object Object] Boolean]
             #^{:static true} [getItem [Object Object] Object]
             #^{:static true} [setItem [Object Object Object] Object]
             #^{:static true} [getGlobal [String] Object]
             #^{:static true} [setGlobal [String Object] Object]
             #^{:static true} [runStringAsInput [String] Object]
             #^{:static true} [runStringAsFile [String] java.util.Map]
             #^{:static true} [importModule [String] Object]
             #^{:static true} [callKw [Object java.util.List java.util.Map] Object]
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
             #^{:static true} [arrayToJVM [Object] java.util.Map]
             #^{:static true} [copyData [Object Object] Object]]
   ))


(set! *warn-on-reflection* true)


(def ^:private requires* (delay
                           (let [require (Clojure/var "clojure.core" "require")]
                             (doseq [clj-ns ["tech.v3.datatype"
                                             "tech.v3.tensor"
                                             "libpython-clj2.python"
                                             "libpython-clj2.python.fn"
                                             "libpython-clj2.python.ffi"
                                             "libpython-clj2.python.gc"
                                             "libpython-clj2.python.np-array"]]
                               (require (Clojure/read clj-ns))))))


(def ^:private ->python* (delay (Clojure/var "libpython-clj2.python" "->python")))
(def ^:private fastcall* (delay (Clojure/var "libpython-clj2.python.fn" "fastcall")))
(def ^:private allocate-fastcall-context* (delay (Clojure/var "libpython-clj2.python.fn" "allocate-fastcall-context")))
(def ^:private release-fastcall-context* (delay (Clojure/var "libpython-clj2.python.fn" "release-fastcall-context")))
(def ^:private make-fastcallable* (delay (Clojure/var "libpython-clj2.python.fn" "make-fastcallable")))
(def ^:private check-error-throw* (delay (Clojure/var "libpython-clj2.python.ffi" "check-error-throw")))
(def ^:private simplify-or-track* (delay (Clojure/var "libpython-clj2.python.ffi" "simplify-or-track")))
(def ^:private as-jvm* (delay (Clojure/var "libpython-clj2.python" "as-jvm")))
(def ^:private eval-code* (delay (Clojure/var "libpython-clj2.python.ffi" "PyEval_EvalCode")))
(def ^:private untracked->python* (delay (Clojure/var "libpython-clj2.python.ffi" "untracked->python")))
(def ^:private decref* (delay (Clojure/var "libpython-clj2.python.ffi" "Py_DecRef")))


(defn- decref-lru-cache
  ^Map [^long max-size]
  (let [builder (CacheBuilder/newBuilder)]
    (.maximumSize builder max-size)
    (.removalListener builder (reify RemovalListener
                                (onRemoval [this notification]
                                  (@decref* (.getValue notification)))))
    (-> (.build builder)
        (.asMap))))


(def ^{:tag Map :private true} string-cache (decref-lru-cache 1024))
(declare -lockGIL -unlockGIL)
(def ^:private globals*
  (delay
    (let [gstate (-lockGIL)
          main-mod ((Clojure/var "libpython-clj2.python.ffi" "PyImport_AddModule") "__main__")]
      (-> ((Clojure/var "libpython-clj2.python.ffi" "PyModule_GetDict") main-mod)
          (@as-jvm*)))))

(def ^:private strcomp* (delay (let [->python @->python*]
                                 (reify java.util.function.Function
                                   (apply [this data]
                                     (->python data))))))


(declare -createArray)


(def ^{:tag 'Map
       :private true}
  primitive-arrays
  (let [base-arrays [[(byte-array 0) {:length #(alength ^bytes %)
                                      :datatype "int8"}]
                     [(short-array 0) {:length #(alength ^shorts %)
                                       :datatype "int16"}]
                     [(int-array 0) {:length #(alength ^ints %)
                                     :datatype "int32"}]
                     [(long-array 0) {:length #(alength ^longs %)
                                      :datatype "int64"}]
                     [(float-array 0) {:length #(alength ^floats %)
                                       :datatype "float32"}]
                     [(double-array 0) {:length #(alength ^doubles %)
                                        :datatype "float64"}]]
        typeset (HashMap.)]
    (doseq [[ary getter] base-arrays]
      (.put typeset (type ary) getter))
    typeset))


(def ^{:tag 'Set
       :private true}
  array-types
  (let [base-arrays [(byte-array 0)
                     (short-array 0)
                     (int-array 0)
                     (long-array 0)
                     (float-array 0)
                     (double-array 0)]
        array-of-arrays (->> base-arrays
                             (map (fn [ary]
                                    (into-array [ary]))))
        typeset (HashSet.)]

    (doseq [t array-of-arrays]
      (.add typeset (type t)))
    (.addAll typeset (.keySet primitive-arrays))
    typeset))


(defn- to-python
  "Support for auto-converting primitive arrays and array-of-arrays into python."
  [item]
  (let [item-type (type item)]
    (if (.contains array-types item-type)
      (if-let [len-getter (get primitive-arrays item-type)]
        (-createArray (len-getter :datatype)
                      [((len-getter :length) item)]
                      item)
        ;;potential 2d array if all lengths match
        (let [^objects item item
              item-len (alength item)
              [datatype shape]
              (when-let [fitem (when-not (== 0 item-len)
                                 (aget item 0))]
                (let [ary-entry (get primitive-arrays (type fitem))
                      inner-len (get ary-entry :length)
                      ary-dt (get ary-entry :datatype)
                      fitem-len (unchecked-long (inner-len fitem))
                      matching?
                      (loop [idx 1
                             matching? true]
                        (if (< idx item-len)
                          (recur (unchecked-inc idx)
                                 (and matching?
                                      (== fitem-len
                                          (unchecked-long
                                           (if-let [inner-item (aget item idx)]
                                             (inner-len inner-item)
                                             0)))))
                          matching?))]
                  (when matching?
                    [ary-dt [item-len fitem-len]])))]
          (if shape
            (-createArray datatype shape item)
            ;;slower fallback for ragged arrays
            (@->python* item))))
      (@->python* item))))


(def ^:private fast-dict-set-item*
  (delay (let [untracked->python (Clojure/var "libpython-clj2.python.ffi"
                                              "untracked->python")
               ->python @->python*
               decref (Clojure/var "libpython-clj2.python.ffi"
                                   "Py_DecRef")
               pydict-setitem (Clojure/var "libpython-clj2.python.ffi"
                                           "PyDict_SetItem")]
           (fn [dict k v]
             (let [v (untracked->python v to-python)]
               (pydict-setitem dict k v)
               (decref v))))))


(defn- cached-string
  [strval]
  (when strval
    (.computeIfAbsent string-cache strval @strcomp*)))


(def ^{:tag Map :private true} compile-cache (decref-lru-cache 1024))

(def ^:private compiler*
  (delay
    (let [compile-fn (Clojure/var "libpython-clj2.python.ffi"
                                  "Py_CompileString")]
      (reify java.util.function.Function
        (apply [this [strdata run-type]]
          (compile-fn strdata "unnamed.py" (case run-type
                                             :file 257
                                             :input 258)))))))

(defn- compile-string
  [strval run-type]
  (when strval
    (.computeIfAbsent compile-cache [strval run-type] @compiler*)))


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


(defn -initializeEmbedded
  "Initialize python when this library is being called *from* a python program.  In
  that case the system will look for the python symbols in the current executable.
  See the [embedded topic](https://clj-python.github.io/libpython-clj/embedded.html)"
  []
  @requires*
  ((Clojure/var "libpython-clj2.python.ffi" "set-library!") nil))


(defn -lockGIL
  "Attempt to lock the gil.  This is safe to call in a reentrant manner.
  Returns a long representing the gil state that must be passed into unlockGIL.

  See documentation for [pyGILState_Ensure](https://docs.python.org/3/c-api/init.html#c.PyGILState_Ensure).

  Note that the API will do this for you but locking
  the GIL before doing a string of operations is faster than having each operation lock
  the GIL individually."
  []
  ((Clojure/var "libpython-clj2.python.ffi" "lock-gil")))


(defn -unlockGIL
  "Unlock the gil passing in the gilstate returned from lockGIL.  Each call to lockGIL must
  be paired to a call to unlockGIL."
  [gilstate]
  ((Clojure/var "libpython-clj2.python.ffi" "unlock-gil") gilstate))


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


(defn -setGlobal
  "Set a value in the global dict.  This function expects the GIL to be locked - it will
  not lock/unlock it for you."
  [^String varname varval]
  (@fast-dict-set-item* @globals* (cached-string varname) varval)
  nil)


(defn -getGlobal
  "Get a value from the global dict.  This function expects the GIL to be locked - it will
  not lock/unlock it for you."
  [^String varname]
  (-getItem @globals* (cached-string varname)))


(defn -runStringAsInput
  "Run a string returning the result of the last expression.  Strings are compiled and
  live for the life of the interpreter.  This is the equivalent to the python
  [eval](https://docs.python.org/3/library/functions.html#eval) call.

  This function expects the GIL to be locked - it will not lock/unlock it for you.

  Example:

```java
  java_api.setGlobal(\"bid\", 1);
  java_api.setGlobal(\"ask\", 2);
  java_api.runStringAsInput(\"bid-ask\"); //Returns -1
```"
  [strdata]
  (let [pyobj (compile-string strdata :input)]
    (when-not pyobj
      (@check-error-throw*))
    (if-let [script-rt (@eval-code* pyobj @globals* @globals*)]
      (@simplify-or-track* script-rt))))


(defn -runStringAsFile
  "Run a string returning the result of the last expression.  Strings are compiled and
  live for the life of the interpreter.  This is the equivalent to the python
  [exec](https://docs.python.org/3/library/functions.html#exec) all.

  The global context is returned as a java map.

  This function expects the GIL to be locked - it will not lock/unlock it for you.

Example:

```java
   Map globals = java_api.runStringAsFile(\"def calcSpread(bid,ask):\n\treturn bid-ask\n\n\");
   Object spreadFn = globals.get(\"calcSpread\");
   java_api.call(spreadFn, 1, 2); // Returns -1
```"
  [strdata]
  (let [pyobj (compile-string strdata :file)]
    (when-not pyobj
      (@check-error-throw*))
    (@eval-code* pyobj @globals* @globals*)
    (@check-error-throw*)
    @globals*))


(defn -importModule
  "Import a python module.  Module entries can be accessed via `getAttr`."
  [modname]
  ((Clojure/var "libpython-clj2.python" "import-module") modname))


(defn -callKw
  "Call a python callable with keyword arguments.  Note that you don't need this pathway
  to call python methods if you do not need keyword arguments; if the python object is
  callable then it will implement [clojure.lang.IFn](https://clojure.github.io/clojure/javadoc/clojure/lang/IFn.html) and you can use `invoke`."
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


(defn -call
  "Call a clojure `IFn` object.  Python callables implement this interface so this works for
  python objects.  This is a convenience method around casting implementation of
  [clojure.lang.IFn](https://clojure.github.io/clojure/javadoc/clojure/lang/IFn.html) and
  calling `invoke` directly."
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


(defn ^:no-doc -allocateFastcallContext
  "Allocate a fastcall context.  See docs for [[-fastcall]].  The returned context must be
  release via [[-releaseFastcallContext]]."
  []
  (@allocate-fastcall-context*))


(defn ^:no-doc -releaseFastcallContext
  "Release a fastcall context.  See docs for [[-fastcall]].  This is safe to call with
  null and to call multiple times on the same context."
  [ctx]
  (when ctx
    (@release-fastcall-context* ctx)))


(defn ^:no-doc -fastcall
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
     tightloop: {
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
  * `shape` - integer array of dimension e.g. `[2,3]`.
  * `data` - list or array of data.  This will of course be fastest if the datatype
  of the array matches the requested datatype.

  This does work with array-of-array structures assuming the shape is correct but those
  will be slower than a single primitive array and a shape."
  [datatype shape data]
  (let [reshape (Clojure/var "tech.v3.tensor" "reshape")
        ->python @->python*]
    (-> ((Clojure/var "tech.v3.tensor" "->tensor") data :datatype (keyword datatype))
        (reshape shape)
        (->python))))


(defn -arrayToJVM
  "Copy (efficiently) a numeric numpy array into a jvm map containing keys \"datatype\",
  \"shape\", and a jvm array \"data\" in flattened row-major form."
  [pyobj]
  (let [tens-data ((Clojure/var "libpython-clj2.python" "as-jvm") pyobj)]
    {"datatype" (name ((Clojure/var "tech.v3.datatype" "elemwise-datatype") tens-data))
     "shape" (int-array ((Clojure/var "tech.v3.datatype" "shape") tens-data))
     "data" ((Clojure/var "tech.v3.datatype" "->array") tens-data)}))


(def ^:private copyfn* (delay (Clojure/var "tech.v3.datatype" "copy!")))


(defn -copyData
  "Copy data from a jvm container into a numpy array or back.  This allows you to use a fixed
  preallocated set of numpy (and potentially jvm arrays) to transfer data back and forth
  efficiently.  The most efficient transfer will be from a java primitive array that matches
  the numeric type of the numpy array.  Also note the element count of the numpy array
  and the jvm array must match.

  Note this copies *from* the first argument *to* the second argument -- this is reverse the
  normal memcpy argument order!!.  Returns the destination (to) argument.

  Not this does *not* work with array-of-arrays.  It will work with, for instance,
  a numpy matrix of shape [2, 2] and an double array of length 4."
  [from to]
  (@copyfn* from to))
