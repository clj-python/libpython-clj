(ns libpython-clj.jna.sequence
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     *python-library*]
             :as libpy-base]
            [tech.jna.base :as jna-base]
            [tech.jna :as jna]
            [camel-snake-kebab.core :refer [->kebab-case]]))
