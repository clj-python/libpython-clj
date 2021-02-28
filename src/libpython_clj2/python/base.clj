(ns libpython-clj2.python.base
  "Shared basic functionality and wrapper functions"
  (:require [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.ffi :as py-ffi]
            [tech.v3.datatype.ffi :as dt-ffi]
            [camel-snake-kebab.core :as csk])
  (:import [tech.v3.datatype.ffi Pointer]))


(defn ->jvm
  "Copying conversion to the jvm."
  ([obj]
   (when obj
     (py-proto/->jvm obj nil)))
  ([obj opts]
   (when obj
     (py-proto/->jvm obj opts))))


(defn as-jvm
  "Bridge/proxy conversion to the jvm"
  ([obj]
   (as-jvm obj nil))
  ([obj opts]
   (when obj
     (py-proto/as-jvm obj opts))))


(defn ->python
  "Copying conversion to python"
  ([obj]
   (->python obj nil))
  ([obj opts]
   (cond
     (nil? obj)
     (py-ffi/py-none)
     (boolean? obj)
     (if obj (py-ffi/py-true) (py-ffi/py-false))
     :else
     (py-proto/->python obj nil))))


(defn ->python-incref
  [pyobj]
  (let [pyobj (->python pyobj)]
    (py-ffi/Py_IncRef pyobj)
    pyobj))


(defn as-python
  "Bridge/proxy conversion to python"
  ([obj]
   (as-python obj nil))
  ([obj opts]
   (cond
     (nil? obj)
     (py-ffi/py-none)
     (boolean? obj)
     (if obj (py-ffi/py-true) (py-ffi/py-false))
     :else
     (py-proto/as-python obj nil))))


(defn stringable?
  [item]
  (or (keyword? item)
      (string? item)
      (symbol? item)))


(defn stringable
  ^String [item]
  (when (stringable? item)
    (if (string? item)
      item
      (name item))))


;;base defaults for forwarding calls
(extend-type Object
  py-proto/PCopyToJVM
  (->jvm [item options]
    (if (dt-ffi/convertible-to-pointer? item)
      (py-proto/pyobject->jvm item options)
      ;;item is already a jvm object
      item))
  py-proto/PBridgeToJVM
  (as-jvm [item options]
    (if (dt-ffi/convertible-to-pointer? item)
      (py-proto/pyobject-as-jvm item options)
      item))
  py-proto/PPyCallable
  (callable? [this] false)
  py-proto/PPyAttr
  (has-attr? [this attr-name] false)
  py-proto/PPyItem
  (has-item? [this item-name] false)
  py-proto/PPyDir
  (dir [this] []))


(extend-protocol py-proto/PPythonType
  Boolean
  (get-python-type [item] :bool)
  Number
  (get-python-type [item]
    (if (integer? item) :int :float))
  String
  (get-python-type [item] :str)
  Object
  (get-python-type [item] (py-ffi/pyobject-type-kwd item)))


(extend-type Pointer
  py-proto/PCopyToJVM
  (->jvm [item options]
    (py-proto/pyobject->jvm item options))
  py-proto/PBridgeToJVM
  (as-jvm [item options]
    (py-proto/pyobject-as-jvm item options))
  py-proto/PPyDir
  (dir [item]
    (py-ffi/with-decref [dirlist (py-ffi/PyObject_Dir item)]
      (if dirlist
        (->jvm dirlist)
        (py-ffi/check-error-throw))))
  py-proto/PPyAttr
  (has-attr? [item item-name]
    (if (stringable? item-name)
      (= 1 (py-ffi/PyObject_HasAttrString item (stringable item-name)))
      (= 1 (py-ffi/PyObject_HasAttr item (->python item-name nil)))))
  (get-attr [item item-name]
    (->
     (if (stringable? item-name)
       (py-ffi/PyObject_GetAttrString item (stringable item-name))
       (py-ffi/with-decref [item-name (py-ffi/untracked->python item-name ->python)]
         (py-ffi/PyObject_GetAttr item item-name)))
        (py-ffi/simplify-or-track)))
  (set-attr! [item item-name item-value]
    (let [item-val (->python item-value)]
      (if (stringable? item-name)
        (py-ffi/PyObject_SetAttrString item (stringable item-name) item-val)
        (py-ffi/PyObject_SetAttr item (->python item-name) item-val)))
    (py-ffi/check-error-throw)
    nil)
  py-proto/PPyCallable
  (callable? [item]
    (== 1 (long (py-ffi/PyCallable_Check item))))
  py-proto/PPyItem
  (has-item? [item item-name]
    (if (stringable? item-name)
      (= 1 (py-ffi/PyObject_HasAttrString item (stringable item-name)))
      (= 1 (py-ffi/PyObject_HasAttr item (->python item-name)))))
  (get-item [item item-name]
    (py-ffi/with-decref [item-name (py-ffi/untracked->python item-name ->python)]
      (-> (py-ffi/PyObject_GetItem item item-name)
          (py-ffi/simplify-or-track))))
  (set-item! [item item-name item-value]
    (py-ffi/with-decref [item-name (py-ffi/untracked->python item-name ->python)
                         item-val (py-ffi/untracked->python item-value ->python)]
      (py-ffi/PyObject_SetItem item item-name item-val)
      (py-ffi/check-error-throw))
    nil))


(def bool-fn-table
  (->> {"Py_LT" 0
        "Py_LE" 1
        "Py_EQ" 2
        "Py_NE" 3
        "Py_GT" 4
        "Py_GE" 5}
       (map (fn [[k v]]
              [(csk/->kebab-case-keyword k) v]))
       (into {})))


(defn hash-code
  ^long [py-inst]
  (py-ffi/with-gil
    (long (py-ffi/PyObject_Hash py-inst))))


(defn equals?
  "Returns true of the python equals operator returns 1."
  [lhs rhs]
  (py-ffi/with-gil
    (= 1 (py-ffi/PyObject_RichCompareBool (->python lhs)
                                          (->python rhs)
                                          (bool-fn-table :py-eq)))))
