(ns libpython-clj2.python.base
  "Shared basic functionality and wrapper functions"
  (:require [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.ffi :as py-ffi]
            [tech.v3.datatype.ffi :as dt-ffi])
  (:import [tech.v3.datatype.ffi Pointer]))


(defn ->jvm
  "Copying conversion to the jvm."
  ([obj]
   (py-proto/->jvm obj nil))
  ([obj opts]
   (py-proto/->jvm obj opts)))


(defn as-jvm
  "Bridge/proxy conversion to the jvm"
  ([obj]
   (as-jvm obj nil))
  ([obj opts]
   (py-proto/as-jvm obj opts)))


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



(extend-type Pointer
  py-proto/PCopyToJVM
  (->jvm [item options]
    (py-proto/pyobject->jvm item options))
  py-proto/PBridgeToJVM
  (as-jvm [item options]
    (py-proto/pyobject-as-jvm item options))
  py-proto/PPyDir
  (dir [item]
    (when-let [dirlist (py-ffi/PyObject_Dir item)]
      (try
        (py-proto/->jvm dirlist nil)
        (finally
          (py-ffi/Py_DecRef dirlist)))))
  py-proto/PPyAttr
  (has-attr? [item item-name]
    (if (stringable? item-name)
      (= 1 (py-ffi/PyObject_HasAttrString item (stringable item-name)))
      (= 1 (py-ffi/PyObject_HasAttr item (->python item-name nil)))))
  (get-attr [item item-name]
    (-> (if (stringable? item-name)
          (py-ffi/PyObject_GetAttrString item (stringable item-name))
          (py-ffi/PyObject_GetAttr item (->python item-name)))
        (py-ffi/wrap-pyobject)))
  (set-attr! [item item-name item-value]
    (let [item-val (->python item-value)]
      (if (stringable? item-name)
        (py-ffi/PyObject_SetAttrString item (stringable item-name) item-val)
        (py-ffi/PyObject_SetAttr item (->python item-name) item-val))))
  py-proto/PPyCallable
  (callable? [item]
    (== 1 (long (py-ffi/PyCallable_Check item))))
  py-proto/PPyItem
  (has-item? [item item-name]
    (if (stringable? item-name)
      (= 1 (py-ffi/PyObject_HasAttrString item (stringable item-name)))
      (= 1 (py-ffi/PyObject_HasAttr item (->python item-name)))))
  (get-item [item item-name]
    (-> (py-ffi/PyObject_GetItem item (->python item-name))
        (py-ffi/wrap-pyobject)))
  (set-item! [item item-name item-value]
    (let [item-val (->python item-value)]
      (py-ffi/PyObject_SetAttr item (->python item-name) item-val))))
