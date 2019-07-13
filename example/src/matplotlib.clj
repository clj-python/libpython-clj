(ns matplotlib
  (:require [libpython-clj.python :as py]
            [tech.v2.datatype :as dtype]
            [tech.v2.tensor :as tens]))

(comment
  (py/initialize!)
  (def mfig (py/import-module "matplotlib.figure"))
  (def magg (py/import-module "matplotlib.backends.backend_agg"))
  (def np (py/import-module "numpy"))
  (def x (py/call-attr np "linspace" 0 2 100))
  (py/python-type x)
  (py/get-attr x "shape")
  (def plt (py/import-module "matplotlib.pyplot"))


  plt.plot(x, x, label='linear')
  plt.plot(x, x**2, label='quadratic')
  plt.plot(x, x**3, label='cubic')

  (py/call-attr-kw plt "plot" [x x] {"label" "linear"})
  (py/call-attr-kw plt "plot" [x (py/call-attr x "__pow__" 2)] {"label" "quadratic"})
  (py/call-attr-kw plt "plot" [x (py/call-attr x "__pow__" 3)] {"label" "cubic"})
  (py/call-attr plt "xlabel" "x label")
  (py/call-attr plt "ylabel" "y label")
  (py/call-attr plt "title" "Simple Plot")
  (py/call-attr plt "legend")
  (py/call-attr plt "show")
  (def fig (py/call-attr plt "figure"))
  (def canvas (py/get-attr fig "canvas"))
  (def agg-canvas (py/call-attr magg "FigureCanvasAgg" fig))
  (py/call-attr agg-canvas "draw")
  (def np-data (py/call-attr np "array"
                             (py/call-attr agg-canvas "buffer_rgba")))
  (def tens (py/as-tensor np-data))

  (import [java.awt.image BufferedImage])
  (import [javax.imageio ImageIO])
  (require '[clojure.java.io :as io])

  (def bufimage (BufferedImage. 480 640 BufferedImage/TYPE_4BYTE_ABGR))
  (def pixels (-> bufimage
                  (.getRaster)
                  (.getDataBuffer)
                  (.getData)))

  (def ignored (dtype/copy! tens pixels))


  (ImageIO/write bufimage "JPG" (io/file "test.jpg"))

  (ImageIO/write bufimage "PNG" (io/file "test.png"))


  (def bufimage (BufferedImage. 640 480 BufferedImage/TYPE_4BYTE_ABGR))
  (def pixels (-> bufimage
                  (.getRaster)
                  (.getDataBuffer)
                  (.getData)))

  (def ignored (dtype/copy! tens 0 pixels 0
                            (dtype/ecount pixels)
                            {:unchecked? true})))
