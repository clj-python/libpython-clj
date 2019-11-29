(ns keras-simple.core
  "https://machinelearningmastery.com/tutorial-first-neural-network-python-keras/"
  (:require [libpython-clj.python
             :refer [import-module
                     import-as
                     from-import
                     a$ ;;compile time call-attr
                     afn ;;runtime call-attr
                     get-item
                     get-attr
                     python-type
                     call-attr
                     call-attr-kw
                     att-type-map
                     ->py-dict]
             :as py]
            [clojure.pprint :as pp]))


;;Uncomment this line to load a different version of your python shared library:


;;(alter-var-root #'libpython-clj.jna.base/*python-library* (constantly "python3.7m"))


(py/initialize!)


(import-as numpy np)
(import-as builtins builtins)
(from-import builtins slice)
(import-as keras keras)
(import-as keras.models keras-models)
(import-as keras.layers keras-layers)
(import-as ctypes c-types)


(defonce initial-data (a$ np loadtxt "pima-indians-diabetes.data.csv" :delimiter ","))


(def features (get-item initial-data [(slice nil) (slice 0 8)]))

(def labels (get-item initial-data [(slice nil) (slice 8 9)]))

(defn dense-layer
  [output-size & args]
  (apply py/afn keras-layers "Dense" output-size args))


(defn sequential-model
  []
  (a$ keras-models Sequential))


(defn add-layer!
  [model layer]
  (a$ model add layer)
  model)

(defn compile-model!
  [model & args]
  (apply py/afn model "compile" args)
  model)


(def model (-> (sequential-model)
               (add-layer! (dense-layer 12 :input_dim 8 :activation :relu))
               (add-layer! (dense-layer 8 :activation :relu))
               (add-layer! (dense-layer 1 :activation :sigmoid))
               (compile-model! :loss :binary_crossentropy
                               :optimizer :adam
                               :metrics (py/->py-list [:accuracy]))))

(defn fit-model
  [model features labels & args]
  (apply py/afn model "fit" features labels args)
  model)


(def fitted-model (fit-model model features labels
                             :epochs 150
                             :batch_size 10))


(defn eval-model
  [model features lables]
  (let [model-names (->> (get-attr model "metrics_names")
                         (mapv keyword))]
    (->> (a$ model evaluate features labels)
         (map vector model-names)
         (into {}))))


(def scores (eval-model fitted-model features labels))


(pp/pprint scores)
