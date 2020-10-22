(ns libpython-clj.python.np-array
  (:require [tech.v3.datatype.protocols :as dtype-proto]
            [tech.v3.tensor :as dtt]
            [libpython-clj.python.interpreter :as py-interp]
            [libpython-clj.python.protocols :as py-proto]
            [libpython-clj.python.bridge :as py-bridge]
            [libpython-clj.python.interop :as py-interop]))


(defmethod py-proto/pyobject->jvm :ndarray
  [pyobj]
  (-> (py-bridge/numpy->desc pyobj)
      (dtt/nd-buffer-descriptor->tensor)
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
       dtype-proto/PToTensor
       (as-tensor [item]
                  (-> (py-bridge/numpy->desc item)
                      dtt/nd-buffer-descriptor->tensor))
       dtype-proto/PElemwiseDatatype
       (elemwise-datatype
        [this]
        (-> (py-proto/get-attr pyobj "dtype")
            (py-proto/as-jvm {})
            (py-bridge/obj-dtype->dtype)))


       dtype-proto/PECount
       (ecount [this] (apply * (dtype-proto/shape this)))

       dtype-proto/PShape
       (shape
        [this]
        (-> (py-proto/get-attr pyobj "shape")
            (py-proto/->jvm {})))

       dtype-proto/PToNativeBuffer
       (convertible-to-native-buffer? [item] true)
       (->native-buffer
        [item]
        (dtype-proto/->native-buffer
         (dtype-proto/as-tensor item)))

       dtype-proto/PSubBuffer
       (sub-buffer
        [buffer offset length]
        (-> (dtype-proto/as-tensor buffer)
            (dtype-proto/sub-buffer offset length)))

       dtype-proto/PToNDBufferDesc
       (convertible-to-nd-buffer-desc? [item] true)
       (->nd-buffer-descriptor
        [item]
        (py-bridge/numpy->desc item))))))


(def np-mod
  (py-bridge/pydelay
   (-> (py-interop/import-module "numpy")
       (py-proto/as-jvm {}))))


(defn- dispatch-unary-op
  [op lhs options]
  (if (keyword? op)
    (if (= op :-)
      (py-proto/call-attr lhs "__neg__")
      (py-proto/call-attr @np-mod (name op) lhs))
    (throw (Exception. "Unimplemented"))))


(defn- dispatch-binary-op
  [op lhs rhs options]
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
