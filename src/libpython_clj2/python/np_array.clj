(ns libpython-clj2.python.np-array
  "Bindings for deeper intergration of numpy into the tech.v3.datatype system.  This
  allows seamless usage of numpy arrays in datatype and tensor functionality such as
  enabling the tech.v3.tensor/ensure-tensor call to work with numpy arrays -- using
  zero copying when possible.

  All users need to do is call require this namespace; then as-jvm will convert a numpy
  array into a tech tensor in-place."
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.fn :as py-fn]
            [libpython-clj2.python.protocols :as py-proto]
            [libpython-clj2.python.copy :as py-copy]
            [libpython-clj2.python.bridge-as-jvm :as py-bridge]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.gc :as pygc]
            [tech.v3.tensor :as dtt]
            [tech.v3.datatype.protocols :as dtype-proto]
            [tech.v3.datatype.casting :as casting]
            [tech.v3.datatype.argops :as argops]
            [tech.v3.datatype :as dtype]
            [clojure.set :as set])
  (:import [tech.v3.datatype NDBuffer]))


(def py-dtype->dtype-map
  (->> (concat (for [bit-width [8 16 32 64]
                     unsigned? [true false]]
                 (str (if unsigned?
                        "uint"
                        "int")
                      bit-width))
               ["float32" "float64"])
       (map (juxt identity keyword))
       (into {})))


(def dtype->py-dtype-map
  (set/map-invert py-dtype->dtype-map))


(defn obj-dtype->dtype
  [py-dtype]
  (when-let [fields (py-proto/get-attr py-dtype "fields")]
    (throw (ex-info (format "Cannot convert numpy object with fields: %s"
                            (py-fn/call-attr fields "__str__" nil))
                    {})))
  (if-let [retval (->> (py-proto/get-attr py-dtype "name")
                       (get py-dtype->dtype-map))]
    retval
    (throw (ex-info (format "Unable to find datatype: %s"
                            (py-proto/get-attr py-dtype "name"))
                    {}))))


(defn numpy->desc
  [np-obj]
  (py-ffi/with-gil
    (let [ctypes (py-proto/as-jvm (py-proto/get-attr np-obj "ctypes") {})
          np-dtype (-> (py-proto/as-jvm (py-proto/get-attr np-obj "dtype") {})
                       (obj-dtype->dtype))
          shape (-> (py-proto/get-attr ctypes "shape")
                    (py-bridge/generic-python-as-list)
                    vec)
          strides (-> (py-proto/get-attr ctypes "strides")
                      (py-bridge/generic-python-as-list)
                      vec)
          long-addr (py-proto/get-attr ctypes "data")]
      {:ptr long-addr
       :elemwise-datatype np-dtype
       :shape shape
       :strides strides
       :type :numpy
       :ctypes ctypes})))


(defmethod py-proto/pyobject->jvm :ndarray
  [pyobj opts]
  (pygc/with-stack-context
    (-> (numpy->desc pyobj)
        (dtt/nd-buffer-descriptor->tensor)
        (dtt/clone))))


(defmethod py-proto/pyobject-as-jvm :ndarray
  [pyobj opts]
  (py-bridge/bridge-pyobject
   pyobj
   Iterable
   (iterator [this] (py-bridge/python->jvm-iterator pyobj py-base/as-jvm))
   dtype-proto/PToTensor
   (as-tensor [item]
              (-> (numpy->desc item)
                  (dtt/nd-buffer-descriptor->tensor)))
   dtype-proto/PElemwiseDatatype
   (elemwise-datatype
    [this]
    (py-ffi/with-gil
      (-> (py-proto/get-attr pyobj "dtype")
          (py-proto/as-jvm {})
          (obj-dtype->dtype))))
   dtype-proto/PECount
   (ecount [this] (apply * (dtype-proto/shape this)))

   dtype-proto/PShape
   (shape
    [this]
    (py-ffi/with-gil
      (-> (py-proto/get-attr pyobj "shape")
          (py-proto/->jvm {}))))

   dtype-proto/PToNativeBuffer
   (convertible-to-native-buffer? [item] true)
   (->native-buffer
    [item]
    (py-ffi/with-gil
      (dtype-proto/->native-buffer
       (dtype-proto/as-tensor item))))

   dtype-proto/PSubBuffer
   (sub-buffer
    [buffer offset length]
    (py-ffi/with-gil
      (-> (dtype-proto/as-tensor buffer)
          (dtype-proto/sub-buffer offset length))))

   dtype-proto/PToNDBufferDesc
   (convertible-to-nd-buffer-desc? [item] true)
   (->nd-buffer-descriptor
    [item]
    (py-ffi/with-gil
      (numpy->desc item)))))


(defn datatype->ptr-type-name
  [dtype]
  (case dtype
    :int8 "c_byte"
    :uint8 "c_ubyte"
    :int16 "c_short"
    :uint16 "c_ushort"
    :int32 "c_long"
    :uint32 "c_ulong"
    :int64 "c_longlong"
    :uint64 "c_ulonglong"
    :float32 "c_float"
    :float64 "c_double"))


(defn descriptor->numpy
  [{:keys [ptr shape strides elemwise-datatype] :as buffer-desc}]
  (py-ffi/with-gil
    (let [stride-tricks (-> (py-ffi/import-module "numpy.lib.stride_tricks")
                            (py-base/as-jvm))
          ctypes (-> (py-ffi/import-module "ctypes")
                     (py-base/as-jvm))
          np-ctypes (-> (py-ffi/import-module "numpy.ctypeslib")
                        (py-base/as-jvm))
          dtype-size (casting/numeric-byte-width elemwise-datatype)
          max-stride-idx (argops/argmax strides)
          buffer-len (* (long (dtype/get-value shape max-stride-idx))
                        (long (dtype/get-value strides max-stride-idx)))
          n-elems (quot buffer-len dtype-size)
          lvalue (long ptr)
          void-p (py-fn/call-attr ctypes "c_void_p" [lvalue])
          actual-ptr (py-fn/call-attr
                      ctypes "cast"
                      [void-p
                       (py-fn/call-attr
                        ctypes "POINTER"
                        [(py-proto/get-attr
                          ctypes
                          (datatype->ptr-type-name elemwise-datatype))])])

          initial-buffer (py-fn/call-attr
                          np-ctypes "as_array"
                          [actual-ptr (py-copy/->py-tuple [n-elems])])

          retval (py-fn/call-attr stride-tricks "as_strided"
                                  [initial-buffer
                                   (py-copy/->py-tuple shape)
                                   (py-copy/->py-tuple strides)])]
      ;;Ensure we have metadata that allows the GC to track both buffer
      ;;desc and retval
      (vary-meta retval assoc
                 :nd-buffer-descriptor buffer-desc))))


;;Efficient conversion from jvm to python
(extend-type NDBuffer
  py-proto/PCopyToPython
  (->python [item opts]
    (-> (dtt/ensure-nd-buffer-descriptor item)
        (descriptor->numpy)))
  py-proto/PBridgeToJVM
  (as-python [item opts]
    (when (dtype-proto/convertible-to-nd-buffer-desc? item)
      (-> (dtype-proto/->nd-buffer-descriptor item)
          (descriptor->numpy)))))
