(ns libpython-clj2.java-api
  "A java api is exposed for libpython-clj2.  The methods below are statically callable without the leading '-'.
  Note that returned python objects implement the respective java interfaces so a python dict will implement
  java.util.Map, etc.  There is some startup time as Clojure dynamically compiles the source code but
  this binding should have great runtime characteristics in comparison to any other java python engine.

```java
import libpython_clj2.java_api;



  java_api.initialize(null);
  np = java_api.importModule(\"numpy\");
  clojure.lang.IFn ones = (clojure.lang.IFn)java_api.getAttr(np, \"ones\");
  ArrayList dims = new ArrayList();
  dims.add(2);
  dims.add(3);
  Object npArray = ones.invoke(dims);

```"
  (:import [java.util Map Map$Entry]
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
  (int ((Clojure/var "libpython-clj2.python.ffi" "PyGILState_Ensure"))))


(defn -unlockGIL
  "Unlock the gil passing in the gilstate returned from lockGIL.  Each call to lockGIL must
  be paired to a call to unlockGIL."
  [gilstate]
  (let [gilstate (int gilstate)]
    (when (== 1 gilstate)
      ((Clojure/var "libpython-clj2.python.gc" "clear-reference-queue")))
    ((Clojure/var "libpython-clj2.python.ffi" "PyGILState_Release") gilstate)))


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
  * `datatype` - One of \"int8\" \"uint8\"  "
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
