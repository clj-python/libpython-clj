(ns libpython-clj.export-module-symbols
  "A macro that will export all the symbols from a python module and make
  them functions in the current clojure namespace.

  DEPRECATED - please use 'libpython-clj.require/require-python"
  (:require [libpython-clj.python :as py]))


(def primitive-types #{:float :int :bool :string})


(defmacro export-module-symbols
  "DEPRECATED - please use 'libpython-clj.require/require-python"
  [py-mod-name]
  (py/initialize!)
  (let [mod-data (py/import-module py-mod-name)]
    `(do ~@(->> (py/att-type-map mod-data)
                (map (fn [[att-name att-type]]
                       (let [att-value (py/get-attr mod-data att-name)
                             doc-str (if (contains? primitive-types att-type)
                                       (str py-mod-name "." att-value)
                                       (try
                                         (py/get-attr att-value "__doc__")
                                         (catch Throwable e "")))]
                         `(def ~(with-meta (symbol att-name)
                                  {:doc doc-str
                                   :py-module py-mod-name})
                            (-> (py/import-module ~py-mod-name)
                                (py/get-attr ~att-name))))))))))
