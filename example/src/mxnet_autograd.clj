(ns mxnet-autograd
  "https://mxnet.incubator.apache.org/api/python/docs/api/autograd/index.html"
  (:require [libpython-clj.python :as py]))


(comment

  (py/initialize!)

  (def mx (py/import-module "mxnet"))

  (def mx-nd (py/get-attr mx "nd"))

  (def autograd (py/get-attr mx "autograd"))

  (def x (py/call-attr mx-nd "ones" 1))

  (py/call-attr x "attach_grad")

  (def z
    (py/with
     [r (py/call-attr autograd "record")]
     (py/call-attr mx-nd "elemwise_add"
                   (py/call-attr mx-nd "exp" x)
                   x)))
  (def dx (py/call-attr-kw autograd "grad" [z [x]] {:create_graph true}))

  (def x (-> (py/call-attr mx-nd "arange" 4)
             (py/call-attr "reshape" [4 1])))

  (py/call-attr x "attach_grad")

  (def y (py/with
          [r (py/call-attr autograd "record")]
          (->> (py/call-attr mx-nd "dot"
                             (py/get-attr x "T")
                             x)
               (py/call-attr mx-nd "multiply" 2))))

  (py/call-attr-kw y "backward" [] {:retain_graph true})

  (py/get-attr x "grad")

  x


  (def mx-rand (py/get-attr mx-nd "random"))

  (def x (py/call-attr-kw mx-rand "uniform" [] {:shape 10}))

  (py/call-attr x "attach_grad")

  (def m (py/with
          [r (py/call-attr autograd "record")]
          (py/call-attr mx-nd "sigmoid" x)))


  (py/call-attr m "backward")

  x
  (py/get-attr x "grad")

  )
