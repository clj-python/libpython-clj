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
  * tuple (persistent vector of length less than 8)


  ")


(defprotocol PPythonType
  (get-python-type [item]
    "Return a keyword that describes the python datatype of this object."))


(defn python-type
  [item]
  (if item
    (get-python-type item)
    :none-type))


(defprotocol PToPython
  (as-python [item]
    "Aside from atom types, this means the object represented by a zero copy python
    mirror.  May return nil.")
  (->python [item]
    "Copy this item into a python representation.  Must never return nil."))


(extend-type Object
  PToPython
  (as-python [item] nil)
  (->python [item]
    (throw (ex-info (format "->python for %s not implemented"
                            (type item)) {}))))


(defmulti as-jvm python-type)
(defmulti ->jvm python-type)
