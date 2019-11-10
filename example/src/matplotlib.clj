(ns matplotlib
  (:require [libpython-clj.python :as py]
            [tech.v2.datatype :as dtype]
            [tech.libs.buffered-image :as bufimg]
            [tech.v2.tensor :as dtt]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]))

(comment

  ;;First let's use matplotlib normally

  (py/initialize!)
  (def mfig (py/import-module "matplotlib.figure"))
  (def magg (py/import-module "matplotlib.backends.backend_agg"))
  (def np (py/import-module "numpy"))
  (def x (py/call-attr np "linspace" 0 2 100))
  (py/python-type x)
  (py/get-attr x "shape")
  (def plt (py/import-module "matplotlib.pyplot"))


  ;; plt.plot(x, x, label='linear')
  ;; plt.plot(x, x**2, label='quadratic')
  ;; plt.plot(x, x**3, label='cubic')

  (defn plot-it
    []
    (py/call-attr-kw plt "plot" [x x] {"label" "linear"})
    (py/call-attr-kw plt "plot" [x (py/call-attr x "__pow__" 2)] {"label" "quadratic"})
    (py/call-attr-kw plt "plot" [x (py/call-attr x "__pow__" 3)] {"label" "cubic"})
    (py/call-attr plt "xlabel" "x label")
    (py/call-attr plt "ylabel" "y label")
    (py/call-attr plt "title" "Simple Plot")
    (py/call-attr plt "legend"))

  (plot-it)

  (py/call-attr plt "show")



  ;;Now let's get a version of this in memory on the JVM where we
  ;;can do something with it.

  (def fig (py/call-attr plt "figure"))
  (def canvas (py/get-attr fig "canvas"))
  (def agg-canvas (py/call-attr magg "FigureCanvasAgg" fig))
  ;; Redo all the drawing above
  (plot-it)

  (py/call-attr agg-canvas "draw")
  (def np-data (py/call-attr np "array"
                             (py/call-attr agg-canvas "buffer_rgba")))
  (def tens (py/as-tensor np-data))

  (dtype/get-datatype tens)
  (dtype/shape tens)
  (dtt/tensor->buffer tens)

  (def bufimage (bufimg/new-image 480 640 :byte-abgr))

  (def ignored (dtype/copy! tens bufimage))

  ;;Now we fix the ordering (RGBA->ABGR):

  (def ignored (-> (dtt/select tens :all :all (->> (range 4)
                                                   reverse))
                   (dtype/copy! bufimage)))

  (bufimg/save! bufimage "test.png")

  )
