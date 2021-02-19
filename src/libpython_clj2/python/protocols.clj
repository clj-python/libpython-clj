(ns libpython-clj2.python.protocols
  (:require [libpython-clj2.python.ffi :as py-ffi]))


(defprotocol PPythonType
  (get-python-type [item]
    "Return a keyword that describes the python datatype of this object."))


(defn python-type
  "Return a keyword that describes the python datatype of this object."
  [item]
  (if item
    (py-ffi/pyobject-type-kwd item)
    :none-type))


(defmulti pyobject-as-jvm
  "Convert (possibly bridge) a pyobject to the jvm"
  python-type)


(defprotocol PBridgeToPython
  (as-python [item options]
    "Aside from atom types, this means the object represented by a zero copy python
    mirror.  May return nil.  This convertible to pointers get converted
    to numpy implementations that share the backing store."))


(defprotocol PPyDir
  (dir [item]
    "Get sorted list of all attribute names."))


(defprotocol PPyAttr
  (has-attr? [item item-name] "Return true of object has attribute")
  (get-attr [item item-name] "Get attribute from object")
  (set-attr! [item item-name item-value] "Set attribute on object"))


(defprotocol PPyCallable
  (callable? [item] "Return true if object is a python callable object."))


(defprotocol PPyItem
  (has-item? [item item-name] "Return true of object has item")
  (get-item [item item-name] "Get an item of a given name from an object")
  (set-item! [item item-name item-value] "Set an item of to a value"))


(defmulti ->jvm
  "Copy a python object to the jvm."
  (fn [item & [options]]
    (python-type item)))


(defprotocol PBridgeToJVM
  (as-jvm [item options]
    "Return a pyobject implementation that wraps the python object."))
