(ns libpython-clj.python.protocols
  "Protocols to help generalize the python bindings.  There is a clear distinction
  made between building a bridging type and thus allowing communication between the
  two systems and building a complete copy of the datastructure of one system in
  another.  Generic objects must only bridge but if we know more about the object
  (like it implements java.util.Map) then we can implement either a bridge or a
  copy.

  Atomic objects:
  * numbers
  * booleans
  * strings-convertible things like strings, keywords, symbols

  Standard containers:
  * list
  * map
  * set
  * tuple (persistent vector of length less than 8)")


(defprotocol PPythonType
  (get-python-type [item]
    "Return a keyword that describes the python datatype of this object."))


(defn python-type
  [item]
  (if item
    (get-python-type item)
    :none-type))


(defprotocol PToPython
  (as-python [item options]
    "Aside from atom types, this means the object represented by a zero copy python
    mirror.  May return nil.  This convertible to pointers get converted
    to numpy implementations that share the backing store.")
  (->python [item options]
    "Copy this item into a python representation.  Must never return nil.
Items may fallback to as-python if copying is untenable."))


(extend-type Object
  PToPython
  (as-python [item options] nil)
  (->python [item options]
    (throw (ex-info (format "->python for %s not implemented"
                            (type item)) {}))))


(defprotocol PToJVM
  (as-jvm [item options]
    "Return a pyobject implementation that wraps the python object.")
  (->jvm [item options]
    "Copy the python object into the jvm leaving no references.  This not copying
are converted into a {:type :pyobject-address} pairs."))


(defprotocol PPyObject
  "Py Objects are functions that given 1 argument return the attribute but if called
  with more than one argument call the given attribute with the rest of the arguments."
  (dir [item]
    "Get sorted list of all attribute names.")
  (att-type-map [item]
    "Get hashmap of att name to keyword datatype.")
  (get-item [item item-name])
  (set-item! [item item-name item-value])
  (as-map [item]
    "Return a Map implementation using __getitem__, __setitem__.  Note that it may be
incomplete especially if the object has no 'keys' attribute.")
  (as-list [item]
    "Return a List implementation using __getitem__, __setitem__.")
  (as-tensor [item]
    "Return a tech.v2.tensor object from the item that shares the data backing store."))


(defmulti pyobject->jvm
  (fn [pyobj]
    (python-type pyobj)))


(defmulti pyobject-as-jvm
  (fn [pyobj]
    (python-type pyobj)))
