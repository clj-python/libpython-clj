(ns libpython-clj2.python.object
  "Bindings to expose python objects to the jvm.  This takes care of marshalling
  types across language boundaries"
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.protocols :as py-proto]
            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.errors :as errors])
  (:import [tech.v3.datatype.ffi Pointer]))


(extend-type Pointer
  py-proto/PPyDir
  (dir [item]
    (py-ffi/with-gil
      (when-let [dirlist (py-ffi/PyObject_Dir item)]
        (try
          (py-proto/->jvm dirlist)
          (finally
            (py-ffi/Py_DecRef dirlist)))))))


(defn python->jvm-copy-hashmap
  [pyobj & [map-items]]
  (py-ffi/with-gil
    (when-not (= 1 (py-ffi/PyMapping_Check pyobj))
      (errors/throwf "Object does not implement the mapping protocol: %s"
                     (py-proto/python-type pyobj)))
    (when-let [map-items (or map-items (py-ffi/PyMapping_Items pyobj))]
      (try
        (->> (py-proto/->jvm map-items)
             (into {}))
        (finally
          (py-ffi/Py_DecRef map-items))))))


(defn python->jvm-copy-persistent-vector
  [pyobj]
  (py-ffi/with-gil
    (when-not (= 1 (py-ffi/PySequence_Check pyobj))
      (errors/throwf "Object does not implement sequence protocol: %s"
                     (py-proto/python-type pyobj)))
    (->> (range (py-ffi/PySequence_Length pyobj))
         (mapv (fn [idx]
                 (let [pyitem (py-ffi/PySequence_GetItem pyobj idx)]
                   (try
                     (py-proto/->jvm pyitem)
                     (finally
                       (py-ffi/Py_DecRef pyitem)))))))))


(defmethod py-proto/->jvm :str
  [pyobj & [options]]
  (py-ffi/pystr->str pyobj))


(defmethod py-proto/->jvm :int
  [pyobj & [options]]
  (py-ffi/PyLong_AsLongLong pyobj))


(defmethod py-proto/->jvm :float
  [pyobj & [options]]
  (py-ffi/PyFloat_AsDouble pyobj))



(defmethod py-proto/->jvm :default
  [pyobj & [options]]
  (py-ffi/with-gil
    (cond
      (= :none-type (py-ffi/pyobject-type-kwd pyobj))
      nil
      ;;Things could implement mapping and sequence logically so mapping
      ;;takes precedence
      (= 1 (py-ffi/PyMapping_Check pyobj))
      (if-let [map-items (py-ffi/PyMapping_Items pyobj)]
        (try
          (python->jvm-copy-hashmap pyobj map-items)
          (finally
            (py-ffi/Py_DecRef map-items)))
        (do
          ;;Ignore error.  The mapping check isn't thorough enough to work for all
          ;;python objects.
          (py-ffi/PyErr_Clear)
          (python->jvm-copy-persistent-vector pyobj)))
      ;;Sequences become persistent vectors
      (= 1 (py-ffi/PySequence_Check pyobj))
      (python->jvm-copy-persistent-vector pyobj)
      :else
      {:type (py-ffi/pyobject-type-kwd pyobj)
       ;;Create a new GC root as the old reference is released.
       :value (let [new-obj (py-ffi/wrap-pyobject
                             (Pointer. (.address (dt-ffi/->pointer pyobj))))]
                (py-ffi/Py_IncRef new-obj)
                new-obj)})))
