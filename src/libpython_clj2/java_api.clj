(ns libpython-clj2.java-api
  (:require [tech.v3.datatype :as dtype]
            [tech.v3.tensor :as dtt]
            [tech.v3.datatype.jvm-map :as jvm-map]
            [libpython-clj2.python :as py]
            [libpython-clj2.python.fn :as py-fn]
            [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.gc :as pygc]
            ;; numpy support
            [libpython-clj2.python.np-array])
  (:import [java.util Map]
           [java.util.function Supplier])
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


(defn -initialize
  "See options for [[libpython-clj2.python/initialize!]].  Note that the keyword
  option arguments can be provided by using strings so `:library-path` becomes
  the key \"library-path\".  Also note that there is still a GIL underlying
  all of the further operations so java should access python via single-threaded
  pathways."
  [options]
  (apply py/initialize! (->> options
                             (mapcat (fn [entry]
                                       [(keyword (jvm-map/entry-key entry))
                                        (jvm-map/entry-value entry)])))))


(defn -lockGIL
  "Attempt to lock the gil.  This is safe to call in a reentrant manner.
  Returns an integer representing the gil state that must be passed into unlockGIL.

  See documentation for [pyGILState_Ensure](https://docs.python.org/3/c-api/init.html#c.PyGILState_Ensure).

  Note that the API will do this for you but locking
  the GIL before doing a string of operations is faster than having each operation lock
  the GIL individually."
  []
  (int (py-ffi/PyGILState_Ensure)))


(defn -unlockGIL
  "Unlock the gil passing in the gilstate returned from lockGIL.  Each call to lockGIL must
  be paired to a call to unlockGIL."
  [gilstate]
  (let [gilstate (int gilstate)]
    (when (== 1 gilstate)
      (pygc/clear-reference-queue))
    (py-ffi/PyGILState_Release gilstate)))


(defn -runString
  "Run a string returning a map of two keys -\"globals\" and \"locals\" which each point to
  a java.util.Map of the respective contexts.  See documentation under
  [[libpython-clj2.python/run-simple-string]] - specifically this will never return
  the result of the last statement ran - you need to set a value in the global or local
  context."
  ^java.util.Map [^String code]
  (->> (py/run-simple-string code)
       (map (fn [[k v]]
              [(name k) v]))
       (into {})))


(defn -inPyContext
  "Run some code in a python context where all python objects allocated within the context
  will be deallocated once the context is released.  The code must not return references
  to live python objects."
  [^Supplier fn]
  (py/with-gil-stack-rc-context
    (-> (.get fn)
        (py/->jvm))))


(defn -hasAttr
  "Returns true if this python object has this attribute."
  [pyobj attname]
  (boolean (py/has-attr? pyobj attname)))


(defn -getAttr
  "Get a python attribute.  This corresponds to the python '.' operator."
  [pyobj attname]
  (py/get-attr pyobj attname))


(defn -setAttr
  "Set an attribute on a python object.  This corresponds to the `__setattr` python call."
  [pyobj attname objval]
  (py/set-attr! pyobj attname objval))


(defn -hasItem
  "Return true if this pyobj has this item"
  [pyobj itemName]
  (boolean (py/has-item? pyobj itemName)))


(defn -getItem
  [pyobj itemName]
  (py/get-item pyobj itemName))


(defn -setItem
  [pyobj itemName itemVal]
  (py/set-item! pyobj itemName itemVal))


(defn -importModule
  "Import a python module.  Module entries can be accessed via `getAttr`."
  [modname]
  (py/import-module modname))


(defn -callKw
  "Call a python callable with keyword arguments.  Note that you don't need this pathway
  to call python methods if you do not need keyword arguments; if the python object is
  callable then it will implement `clojure.lang.IFn` and you can use `invoke`."
  [pyobj pos-args kw-args]
  (py/with-gil (py-fn/call-kw pyobj pos-args (->> kw-args
                                                  (map (fn [entry]
                                                         [(jvm-map/entry-key entry)
                                                          (jvm-map/entry-value entry)]))
                                                  (into {})))))

(defn -copyToPy
  "Copy a basic jvm object, such as an implementation of java.util.Map or java.util.List to
  a python object."
  [object]
  (py/->python object))


(defn -copyToJVM
  "Copy a python object such as a dict or a list into a comparable JVM object."
  [object]
  (py/->jvm object))


(defn -createArray
  "Create a numpy array from a tuple of string datatype, shape and data.
  * `datatype` - One of \"int8\" \"uint8\"  "
  [datatype shape data]
  (-> (dtt/->tensor data :datatype (keyword datatype))
      (dtt/reshape shape)
      (py/->python)))


(defn -arrayToJVM
  "Copy (efficiently) a numeric numpy array into a jvm map containing keys \"datatype\",
  \"shape\", and \"data\"."
  [pyobj]
  (let [tens-data (py/as-jvm pyobj)]
    {"datatype" (name (dtype/elemwise-datatype tens-data))
     "shape" (int-array (dtype/shape tens-data))
     "data" (dtype/->array tens-data)}))
