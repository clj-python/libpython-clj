(ns keras-simple.core
  "https://machinelearningmastery.com/tutorial-first-neural-network-python-keras/"
  (:require [libpython-clj.python
             :refer [import-module
                     item
                     attr
                     python-type
                     call-attr
                     call-attr-kw
                     att-type-map
                     ->py-dict]
             :as py]
            [clojure.pprint :as pp]))


(py/initialize!)


(defonce np (import-module "numpy"))
(defonce builtins (import-module "builtins"))
(defonce keras (import-module "keras"))
(defonce keras-models (import-module "keras.models"))
(defonce keras-layers (import-module "keras.layers"))
(defonce c-types (import-module "ctypes"))

(defn slice
  ([]
   (call-attr builtins "slice" nil))
  ([start]
   (call-attr builtins "slice" start))
  ([start stop]
   (call-attr builtins "slice" start stop))
  ([start stop incr]
   (call-attr builtins "slice" start stop incr)))


(defonce initial-data (call-attr-kw np "loadtxt"
                                    ["pima-indians-diabetes.data.csv"]
                                    {"delimiter" ","}))


(defonce features (call-attr initial-data "__getitem__"
                            [(slice) (slice 0 8)]))

(defonce labels (call-attr initial-data "__getitem__"
                           [(slice) (slice 8 9)]))


(defn dense-layer
  [output-size & {:as kwords}]
  (call-attr-kw keras-layers "Dense" [output-size] kwords))


(defn sequential-model
  []
  (call-attr keras-models "Sequential"))


(defn add-layer!
  [model layer]
  (call-attr model "add" layer)
  model)

(defn compile-model!
  [model & {:as kw-args}]
  (call-attr-kw model "compile" []
                kw-args)
  model)


(def model (-> (sequential-model)
                   (add-layer! (dense-layer 12 "input_dim" 8 "activation" "relu"))
                   (add-layer! (dense-layer 8 "activation" "relu"))
                   (add-layer! (dense-layer 1 "activation" "sigmoid"))
                   (compile-model! "loss" "binary_crossentropy"
                                   "optimizer" "adam"
                                   "metrics" (py/->py-list ["accuracy"]))))

;;model.compile(loss='binary_crossentropy', optimizer='adam', metrics=['accuracy'])

(defn fit-model
  [model features labels & {:as kw-args}]
  (call-attr-kw model "fit"
                [features labels]
                kw-args)
  model)

(defonce fitted-model (fit-model model features labels
                                 "epochs" 150
                                 "batch_size" 10))


(defn numpy-num->jvm
  [np-obj]
  (when-not (= (py/python-type np-obj) :float-64)
    (throw (ex-info "Incorrect python type." {})))
  (-> (py/attr np-obj "data")
      (call-attr "__getitem__" (py/->py-tuple []))))


(defn eval-model
  [model features lables]
  (let [model-names (->> (py/attr model "metrics_names")
                         (mapv keyword))]
    (->> (call-attr model "evaluate" features labels)
         (map numpy-num->jvm)
         (map vector model-names)
         (into {}))))


(def scores (eval-model fitted-model features labels))


(pp/pprint scores)
