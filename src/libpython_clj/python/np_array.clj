(ns libpython-clj.python.np-array
  (:require [tech.v2.datatype.protocols :as dtype-proto]
            [tech.v2.datatype.operation-provider :as op-provider]
            [tech.v2.tensor :as dtt]
            [tech.v2.datatype.argtypes :as argtypes]
            [libpython-clj.python.interpreter :as py-interp]
            [libpython-clj.python.protocols :as py-proto]
            [libpython-clj.python.bridge :as py-bridge]
            [libpython-clj.python.object :as py-object]
            [libpython-clj.python.interop :as py-interop]
            [tech.jna :as jna]))



(defmethod py-proto/pyobject->jvm :ndarray
  [pyobj]
  (-> (py-bridge/numpy->desc pyobj)
      (dtt/buffer-descriptor->tensor)
      (dtt/clone)))


(defmethod py-proto/pyobject-as-jvm :ndarray
  [pyobj]
  (py-interp/with-gil
    (let [interpreter (py-interp/ensure-bound-interpreter)]
      (py-bridge/bridge-pyobject
       pyobj
       interpreter
       Iterable
       (iterator [this]
                 (py-proto/python-obj-iterator pyobj interpreter))
       py-proto/PPyObjectBridgeToMap
       (as-map [item]
               (py-bridge/generic-python-as-map pyobj))
       py-proto/PPyObjectBridgeToList
       (as-list [item]
                (py-bridge/generic-python-as-list pyobj))
       py-proto/PPyObjectBridgeToTensor
       (as-tensor [item]
                  (-> (py-bridge/numpy->desc item)
                      dtt/buffer-descriptor->tensor))
       dtype-proto/PDatatype
       (get-datatype
        [this]
        (-> (py-proto/get-attr pyobj "dtype")
            (py-proto/as-jvm {})
            (py-bridge/obj-dtype->dtype)))


       dtype-proto/POperationType
       (operation-type
        [this]
        :numpy-array)

       dtype-proto/PCountable
       (ecount [this] (apply * (dtype-proto/shape this)))

       dtype-proto/PShape
       (shape
        [this]
        (-> (py-proto/get-attr pyobj "shape")
            (py-proto/->jvm {})))

       dtype-proto/PToNioBuffer
       (convertible-to-nio-buffer? [item] true)
       (->buffer-backing-store
        [item]
        (-> (py-proto/as-tensor item)
            (dtype-proto/->buffer-backing-store)))

       dtype-proto/PToJNAPointer
       (convertible-to-data-ptr? [item] true)
       (->jna-ptr
        [item]
        (:ptr (py-bridge/numpy->desc item)))

       dtype-proto/PBuffer
       (sub-buffer
        [buffer offset length]
        (-> (py-proto/as-tensor buffer)
            (dtype-proto/sub-buffer offset length)))

       dtype-proto/PToBufferDesc
       (convertible-to-buffer-desc? [item] true)
       (->buffer-descriptor
        [item]
        (py-bridge/numpy->desc item))

       dtype-proto/PToReader
       (convertible-to-reader? [item] true)
       (->reader
        [item options]
        (-> (py-proto/as-tensor item)
            (dtype-proto/->reader options)))

       dtype-proto/PToWriter
       (convertible-to-writer? [item] true)
       (->writer
        [item options]
        (-> (py-proto/as-tensor item)
            (dtype-proto/->writer options)))))))


(def np-mod
  (py-bridge/pydelay
   (-> (py-interop/import-module "numpy")
       (py-proto/as-jvm {}))))


(def default-provider
  (reify
    op-provider/POperationProvider
    (argtype [provider lhs] (argtypes/arg->arg-type lhs))
    (cast [provider lhs options]
      (when-not (keyword? (:datatype options))
        (throw (Exception. "Can only case to base datatypes")))
      (let [pytype (py-proto/get-attr @np-mod (name (:datatype options)))]
        (py-proto/call-attr lhs "astype" pytype)))

    (unary-op [provider lhs op options]
      (if (= op :-)
        (py-proto/call-attr lhs "__neg__")
        (py-proto/call-attr @np-mod (name op) lhs)))

    (binary-op [provider lhs rhs op options]
      (case op
        :max (py-proto/call-attr @np-mod "max" lhs rhs)
        :min (py-proto/call-attr @np-mod "min" lhs rhs)
        :+ (py-proto/call-attr @np-mod "add" lhs rhs)
        :- (py-proto/call-attr @np-mod "subtract" lhs rhs)
        :div (py-proto/call-attr @np-mod "divide" lhs rhs)
        :* (py-proto/call-attr @np-mod "multiply" lhs rhs)
        :pow (py-proto/call-attr @np-mod "power" lhs rhs)
        :quot (py-proto/call-attr @np-mod "floor_divide" lhs rhs)
        :rem (py-proto/call-attr @np-mod "mod" lhs rhs)
        :bit-and (py-proto/call-attr @np-mod "bitwise_and" lhs rhs)
        :bit-flip (py-proto/call-attr @np-mod "bitwise_not" lhs rhs)
        :bit-or (py-proto/call-attr @np-mod "bitwise_or" lhs rhs)
        :bit-xor (py-proto/call-attr @np-mod "bitwise_xor" lhs rhs)
        :bit-shift-left (py-proto/call-attr @np-mod "left_shift" lhs rhs)
        :bit-shift-right (py-proto/call-attr @np-mod "right_shift" lhs rhs)
        :and (py-proto/call-attr @np-mod "logical_and" lhs rhs)
        :or (py-proto/call-attr @np-mod "logical_or" lhs rhs)
        :not (py-proto/call-attr @np-mod "logical_not" lhs rhs)
        :xor (py-proto/call-attr @np-mod "logical_xor" lhs rhs)
        :< (py-proto/call-attr @np-mod "less" lhs rhs)
        :<= (py-proto/call-attr @np-mod "less_equal" lhs rhs)
        :eq (py-proto/call-attr @np-mod "equal" lhs rhs)
        :> (py-proto/call-attr @np-mod "greater" lhs rhs)
        :>= (py-proto/call-attr @np-mod "greater_equal" lhs rhs)))

    (reduce-op [provider lhs op options]
      (case op
        :+ (py-proto/call-attr lhs "sum")))))


(defmethod op-provider/unary-provider :numpy-array
  [lhs]
  default-provider)


(defmethod op-provider/binary-provider [:scalar :numpy-array]
  [lhs rhs]
  default-provider)


(defmethod op-provider/binary-provider [:numpy-array :scalar]
  [lhs rhs]
  default-provider)


(defmethod op-provider/binary-provider [:numpy-array :numpy-array]
  [lhs rhs]
  default-provider)
