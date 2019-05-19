(ns libpython-clj.jna.protocols.iterator
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.jna :as jna])
  (:import [com.sun.jna Pointer]))




(def-pylib-fn PyIter_Check
  "Return true if the object o supports the iterator protocol."
  Integer
  [o ensure-pyobj])



(def-pylib-fn PyIter_Next
  "Return value: New reference.

   Return the next value from the iteration o. The object must be an iterator (it is up
   to the caller to check this). If there are no remaining values, returns NULL with no
   exception set. If an error occurs while retrieving the item, returns NULL and passes
   along the exception."
  Pointer
  [o ensure-pyobj])
