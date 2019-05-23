(ns libpython-clj.python
  (:require [libpython-clj.jna :as libpy]
            [libpython-clj.jna.base :as libpy-base]
            [libpython-clj.python.logging
             :refer [log-error log-warn log-info]]
            [tech.jna.base :as jna-base]
            [tech.jna :as jna]
            [tech.resource :as resource]
            [tech.resource.gc :as resource-gc]
            [tech.parallel.require :as parallel-req]
            [tech.v2.datatype :as dtype])
  (:import [tech.resource GCSoftReference]
           [com.sun.jna Pointer Structure CallbackReference]
           [com.sun.jna.ptr PointerByReference
            LongByReference IntByReference]
           [java.lang AutoCloseable]
           [java.nio.charset StandardCharsets]
           [java.lang.reflect Field Method]
           [libpython_clj.jna
            CFunction$KeyWordFunction
            CFunction$TupleFunction
            CFunction$NoArgFunction
            CFunction$tp_new
            CFunction$tp_dealloc
            CFunction$tp_att_getter
            CFunction$tp_att_setter
            CFunction$tp_getattr
            CFunction$tp_getattro
            CFunction$tp_setattr
            CFunction$tp_setattro
            CFunction$tp_hash
            PyMethodDef PyObject
            PyMethodDef$ByReference
            PyTypeObject
            JVMBridge
            JVMBridgeType]
           [tech.v2.datatype ObjectIter]
           [java.io Writer]))


(set! *warn-on-reflection* true)
