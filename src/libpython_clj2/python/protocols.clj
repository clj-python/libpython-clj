(ns libpython-clj2.python.protocols
  "Internal protocols to libpython-clj.  Implementations of these protocols should
  expect the GIL to be captured so they themselves **d not** need to capture
  the GIL."
  (:require [libpython-clj2.python.ffi :as py-ffi]))


(defprotocol PPythonType
  (get-python-type [item]
    "Return a keyword that describes the python datatype of this object."))


(defn python-type
  "Return a keyword that describes the python datatype of this object."
  ([item]
   (if item
     (py-ffi/pyobject-type-kwd item)
     :none-type))
  ([item options]
   (python-type item)))


(defmulti pyobject-as-jvm
  "Convert (possibly bridge) a pyobject to the jvm"
  python-type)


(defprotocol PCopyToPython
  (->python [item options]
    "Copy this item into a python representation.  Must never return nil.
Items may fallback to as-python if copying is untenable."))


(defprotocol PBridgeToPython
  (as-python [item options]
    "Aside from atom types, this means the object represented by a zero copy python
    mirror.  May return nil.  This convertible to pointers get converted
    to numpy implementations that share the backing store."))


(defprotocol PCopyToJVM
  (->jvm [item options]
    "Copy the python object into the jvm leaving no references.  This not copying
are converted into a {:type :pyobject-address} pairs."))


(defprotocol PBridgeToJVM
  (as-jvm [item options]
    "Return a pyobject implementation that wraps the python object."))


(defprotocol PPyDir
  (dir [item]
    "Get sorted list of all attribute names."))


(defprotocol PPyAttr
  (has-attr? [item item-name] "Return true of object has attribute")
  (get-attr [item item-name] "Get attribute from object")
  (set-attr! [item item-name item-value] "Set attribute on object"))


(defprotocol PPyCallable
  (callable? [item] "Return true if object is a python callable object."))


(defprotocol PyCall
  (call [callable arglist kw-arg-map]))


(defprotocol PPyItem
  (has-item? [item item-name] "Return true of object has item")
  (get-item [item item-name] "Get an item of a given name from an object")
  (set-item! [item item-name item-value] "Set an item of to a value"))


(defmulti pyobject->jvm
  "Copy a python object to the jvm based on its python type"
  python-type)


(defmulti pyobject-as-jvm
  "Bridge a python object to the jvm based on its python type"
  python-type)
