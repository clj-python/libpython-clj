(ns libpython-clj.python.object
  (:require [libpython-clj.python.interpreter
             :refer [with-gil
                     with-interpreter
                     ensure-interpreter
                     ensure-bound-interpreter
                     check-error-throw]
             :as pyinterp]
            [libpython-clj.python.logging
             :refer [log-error log-warn log-info]]
            [libpython-clj.jna.base :as libpy-base]
            [libpython-clj.jna :as libpy]
            [tech.jna :as jna]
            [tech.resource :as resource]
            [tech.v2.datatype :as dtype])
  (:import [com.sun.jna Pointer]
           [libpython_clj.jna
            PyObject]
           [java.nio.charset StandardCharsets]
           [tech.v2.datatype ObjectIter]))


(set! *warn-on-reflection* true)


(extend-type Object
  libpy-base/PToPyObjectPtr
  (->py-object-ptr [item]
    (PyObject.
     (jna/as-ptr item))))


(defn ->pyobject
  ^PyObject [item]
  (when-not item
    (throw (ex-info "Null item passed in" {})))
  (libpy-base/->py-object-ptr item))


(defn wrap-pyobject
  "Wrap object such that when it is no longer accessible via the program decref is
  called. Used for new references.  This is some of the meat of the issue, however,
  in that getting the two system's garbage collectors to play nice is kind
  of tough."
  [pyobj]
  (check-error-throw)
  (when pyobj
    (let [interpreter (ensure-bound-interpreter)
          pyobj-value (Pointer/nativeValue (jna/as-ptr pyobj))]
      (comment
        (println "tracking object" pyobj-value))
      ;;We ask the garbage collector to track the python object and notify
      ;;us when it is released.  We then decref on that event.
      (resource/track pyobj
                      #(with-interpreter interpreter
                         (try
                           (comment
                             (println "releasing object" pyobj-value
                                      "with refcount" (.ob_refcnt (PyObject.
                                                                   (Pointer.
                                                                    pyobj-value)))))
                           (libpy/Py_DecRef (Pointer. pyobj-value))
                           (catch Throwable e
                             (log-error "Exception while releasing object: %s" e))))
                      [:gc]))))


(defn incref-wrap-pyobject
  "Increment the object's refcount and then call wrap-pyobject.  Used for borrowed
  references that need to escape the current scope."
  [pyobj]
  (with-gil
    (let [pyobj (jna/as-ptr pyobj)]
      (libpy/Py_IncRef pyobj)
      (wrap-pyobject pyobj))))


(defn incref
  "Incref and return object"
  [pyobj]
  (let [pyobj (jna/as-ptr pyobj)]
    (libpy/Py_IncRef pyobj)
    pyobj))


(defn py-true
  []
  (libpy/Py_True))


(defn py-false
  []
  (libpy/Py_False))


(defn py-none
  []
  (libpy/Py_None))


(defn py-not-implemented
  []
  (libpy/Py_NotImplemented))


(defn py-raw-type
  ^Pointer [pyobj]
  (let [pyobj (->pyobject pyobj)]
    (.ob_type pyobj)))


(defn py-type-keyword
  [pyobj]
  (with-gil
    (-> pyobj
        py-raw-type
        pyinterp/py-type-keyword)))


(defn py-string->string
  ^String [pyobj]
  (with-gil
    (when-not (= :str (py-type-keyword pyobj))
      (throw (ex-info (format "Object passed in is not a string: %s"
                              (py-type-keyword pyobj))
                      {})))
    (let [size-obj (jna/size-t-ref)
          ^Pointer str-ptr (libpy/PyUnicode_AsUTF8AndSize pyobj size-obj)
          n-elems (jna/size-t-ref-value size-obj)]
      (-> (.decode StandardCharsets/UTF_8 (.getByteBuffer str-ptr 0 n-elems))
          (.toString)))))


(defn pyobj->string
  ^String [pyobj]
  (with-gil
    (let [py-str (if (= :str (py-type-keyword pyobj))
                   pyobj
                   (-> (libpy/PyObject_Str pyobj)
                       wrap-pyobject))]
      (py-string->string py-str))))


(defn py-dir
  [pyobj]
  (with-gil
    (let [item-dir (libpy/PyObject_Dir pyobj)]
      (->> (range (libpy/PyObject_Length item-dir))
           (mapv (fn [idx]
                   (-> (libpy/PyObject_GetItem item-dir
                                               (libpy/PyLong_FromLong idx))
                       pyobj->string)))))))


(declare copy-to-python)


(defn ->py-long
  [item]
  (with-gil
    (wrap-pyobject
     (libpy/PyLong_FromLongLong (long item)))))


(defn ->py-float
  [item]
  (with-gil
    (wrap-pyobject
     (libpy/PyFloat_FromDouble (double item)))))


(defn ->py-string
  [item]
  (with-gil
    (let [byte-data (.getBytes ^String item StandardCharsets/UTF_16)]
      (wrap-pyobject
       (libpy/PyUnicode_Decode byte-data (dtype/ecount byte-data)
                               "UTF-16" "strict")))))


(defn ->py-dict
  [item]
  (with-gil
    (let [dict (libpy/PyDict_New)]
      (doseq [[k v] item]
        (libpy/PyDict_SetItem dict (copy-to-python k)
                              (copy-to-python v)))
      (wrap-pyobject
       dict))))


(defn ->py-list
  [item-seq]
  (with-gil
    (let [retval (libpy/PyList_New (count item-seq))]
      (->> item-seq
           (map-indexed (fn [idx item]
                          (libpy/PyList_SetItem
                           retval
                           idx
                           (let [new-val (copy-to-python item)]
                             (libpy/Py_IncRef new-val)
                             new-val))))
           dorun)
      (wrap-pyobject retval))))


(defn ->py-tuple
  [item-seq]
  (with-gil
    (let [n-items (count item-seq)
          new-tuple (libpy/PyTuple_New n-items)]
      (->> item-seq
           (map-indexed (fn [idx item]
                          (libpy/PyTuple_SetItem
                           new-tuple
                           idx
                           (let [new-val (copy-to-python item)]
                             (libpy/Py_IncRef new-val)
                             new-val))))
           dorun)
      (wrap-pyobject new-tuple))))


(defn copy-to-python
  [item]
  (cond
    (integer? item)
    (->py-long item)
    (number? item)
    (->py-float item)
    (string? item)
    (->py-string item)
    (keyword? item)
    (copy-to-python (name item))
    (map? item)
    (->py-dict item)
    (seq item)
    (if (and (< (count item) 4)
             (vector? item))
      (->py-tuple item)
      (->py-list item))
    :else
    (libpy-base/->py-object-ptr item)))


(declare copy-to-jvm has-attr? get-attr)


(defn python->jvm-copy-hashmap
  [pyobj & [map-items]]
  (with-gil nil
    (when-not (= 1 (libpy/PyMapping_Check pyobj))
      (throw (ex-info (format "Object does not implement the mapping protocol: %s"
                              (py-type-keyword pyobj)))))
    (->> (or map-items
             (-> (libpy/PyMapping_Items pyobj))
             wrap-pyobject)
         copy-to-jvm
         (into {}))))


(defn python->jvm-copy-persistent-vector
  [pyobj]
  (with-gil nil
    (when-not (= 1 (libpy/PySequence_Check pyobj))
      (throw (ex-info (format "Object does not implement sequence protocol: %s"
                              (py-type-keyword pyobj)))))

    (->> (range (libpy/PySequence_Length pyobj))
         (mapv (fn [idx]
                 (-> (libpy/PySequence_GetItem pyobj idx)
                     wrap-pyobject
                     copy-to-jvm))))))


(defn python->jvm-copy-iterable
  "Create an iterable that auto-copies what it iterates completely into the jvm."
  [pyobj]
  (with-gil nil
    (when-not (= 1 (has-attr? pyobj "__iter__"))
      (throw (ex-info (format "object is not iterable: %s"
                              (py-type-keyword pyobj))
                      {})))
    (let [iter-callable (get-attr pyobj "__iter__")
          interpreter (ensure-bound-interpreter)]
      (reify Iterable
        (iterator [item]
          (let [py-iter (with-interpreter interpreter
                          (-> (libpy/PyObject_CallObject iter-callable nil)
                              wrap-pyobject))
                next-fn (fn [last-item]
                          (with-interpreter interpreter
                            (when-let [next-obj (libpy/PyIter_Next py-iter)]
                              (-> next-obj
                                  (wrap-pyobject)
                                  (copy-to-jvm)))))
                cur-item-store (atom (next-fn nil))]
            (reify ObjectIter
              (hasNext [obj-iter] (boolean @cur-item-store))
              (next [obj-iter]
                (-> (swap-vals! cur-item-store next-fn)
                    first))
              (current [obj-iter]
                @cur-item-store))))))))


(defn copy-to-jvm
  [pyobj]
  (with-gil nil
    (case (py-type-keyword pyobj)
      :int
      (libpy/PyLong_AsLongLong pyobj)
      :float
      (libpy/PyFloat_AsDouble pyobj)
      :str
      (py-string->string pyobj)
      :none-type
      nil
      :tuple
      (python->jvm-copy-persistent-vector pyobj)
      :list
      (python->jvm-copy-persistent-vector pyobj)
      :dict
      (python->jvm-copy-hashmap pyobj)
      (cond
        ;;Things could implement mapping and sequence logically so mapping
        ;;takes precedence
        (= 1 (libpy/PyMapping_Check pyobj))
        (if-let [map-items (try (-> (libpy/PyMapping_Items pyobj)
                                    wrap-pyobject)
                                (catch Throwable e nil))]
          (python->jvm-copy-hashmap pyobj map-items)
          (do
            ;;Ignore error.  The mapping check isn't thorough enough to work.
            (libpy/PyErr_Clear)
            (python->jvm-copy-persistent-vector pyobj)))
        ;;Sequences become persistent vectors
        (= 1 (libpy/PySequence_Check pyobj))
        (python->jvm-copy-persistent-vector)
        (= 1 (has-attr? pyobj "__iter__"))
        (python->jvm-copy-iterable pyobj)
        :else
        {:type (py-type-keyword pyobj)
         :value pyobj}))))


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


(defn has-attr?
  [pyobj attr-name]
  (with-gil nil
    (if (stringable? attr-name)
      (libpy/PyObject_HasAttrString pyobj (stringable attr-name))
      (libpy/PyObject_HasAttrString pyobj (copy-to-python attr-name)))))


(defn get-attr
  [pyobj attr-name]
  (with-gil nil
    (if (stringable? attr-name)
      (wrap-pyobject (libpy/PyObject_GetAttrString pyobj (stringable attr-name)))
      (wrap-pyobject (libpy/PyObject_GetAttr pyobj (copy-to-python attr-name))))))


(defn set-attr
  [pyobj attr-name attr-value]
  (with-gil nil
    (if (stringable? attr-name)
      (libpy/PyObject_SetAttrString pyobj (stringable attr-name) attr-value)
      (libpy/PyObject_SetAttr pyobj attr-name attr-value))
    pyobj))


(defn obj-has-item?
  [elem elem-name]
  (with-gil nil
    (if (stringable? elem-name)
      (libpy/PyMapping_HasKeyString elem (stringable elem-name))
      (libpy/PyMapping_HasKey elem elem-name))))


(defn obj-get-item
  [elem elem-name]
  (with-gil nil
    (if (stringable? elem-name)
      (libpy/PyMapping_GetItemString elem (stringable elem-name))
      (libpy/PyObject_GetItem elem elem-name))))


(defn obj-set-item
  [elem elem-name elem-value]
  (with-gil nil
    (if (stringable? elem-name)
      (libpy/PyMapping_SetItemString elem (stringable elem-name) elem-value)
      (libpy/PyObject_SetItem elem elem-name elem-value))
    elem))
