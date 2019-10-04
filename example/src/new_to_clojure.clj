(ns new-to-clojure
  "This is a clojure namespace declaration.  This goes at the top, is documented like
  such and can contain information about how to interpret the rest of this file.
  For now, we leave it at that.")


;; This is a clojure line comment.  From here on out we will place everything in a
;; comment form which, as we will see, can container evaluatable code.  If you
;; are in emacs with clojure, leiningen, and cider installed then you can
;; type C-c,C-e to execute each line.

(comment
  ;;First, let's talk about Clojure.  This is a a list
  (list 1 2 3)

  ;;So is this
  '(list 1 2 3)

  ;;When the reader finds a list it applies the first thing in the list
  ;;to everything following the first thing.

  (+ 1 2 3)

  ;;If the first thing isn't executable, that is an error
  (1 2 3)

  ;;Clojure arrays are persisent vectors meaning they are immutable
  ;;datastructures that have good random access semantics.  They are often
  ;;used for data because the reader does *not* attempt to execute them.
  [1 2 3 4]

  (conj [1 2 3 4] 5)

  (map inc [1 2 3 4])

  (filter #(> % 2) [1 2 3 4])

  ;;In Clojure, a dict is called a map and those are also immutable datastructures.
  ;;We specify them like such:
  {:a 1 :b 2}

  (assoc {:a 1 :b 2} :c 3)

  (dissoc {:a 1 :b 2} :b)

  (map identity {:a 1 :b 2})

  (into {} (map identity {:a 1 :b 2}))

  ;;When we want another library, we usually use require expects a clojure library.
  ;;Because we are on them JVM, we sometimes have to use 'import' which targets a
  ;;particular class

  (require '[libpython-clj.python :as py])

  (py/initialize!)

  (def test-dict (py/->python {:a 1 :b 2}))

  (py/python-type test-dict)

  (py/att-type-map test-dict)

  (def bridged (py/as-jvm test-dict))

  (def np (py/import-module "numpy"))

  (-> (py/get-attr np "linspace")
      (py/get-attr "__doc__")
      print)

  ;; Examples
  ;; --------
  ;; >>> np.linspace(2.0, 3.0, num=5)
  ;; array([ 2.  ,  2.25,  2.5 ,  2.75,  3.  ])

  (def np-ary (py/call-attr-kw np "linspace" [2.0 3.0] {:num 5}))

  (def tens (py/as-tensor np-ary))

  (map identity tens)

  (require '[tech.v2.datatype :as dtype])

  (dtype/set-value! tens 2 6677)

  (println np-ary)


  (def mfig (py/import-module "matplotlib.figure"))
  (def magg (py/import-module "matplotlib.backends.backend_agg"))

  (def x (py/call-attr np "linspace" 0 2 100))

  (def plt (py/import-module "matplotlib.pyplot"))

  (def fig (py/call-attr plt "figure"))
  (def canvas (py/get-attr fig "canvas"))
  (def agg-canvas (py/call-attr magg "FigureCanvasAgg" fig))

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

  (py/call-attr agg-canvas "draw")
  (def np-data (py/call-attr np "array"
                             (py/call-attr agg-canvas "buffer_rgba")))
  (def tens (py/as-tensor np-data))

  (require '[tech.v2.tensor :as dtt])
  (import '[java.awt.image BufferedImage])
  (import '[javax.imageio ImageIO])

  (def bufimage (BufferedImage. 480 640 BufferedImage/TYPE_4BYTE_ABGR))

  (def pixels (-> bufimage
                  (.getRaster)
                  (.getDataBuffer)
                  (.getData)))

  ;;Copy will read the data out in row-major format.
  (def ignored (dtype/copy! tens pixels))

  (type pixels)

  ;;That didn't work because pixels is a byte array and the values in tens are
  ;;out of range of a byte.  We can turn off range checking and cause c-style
  ;;casts to happen.

  (def ignored (dtype/copy! tens 0 pixels 0 (dtype/ecount tens) {:unchecked? true}))


  (require '[clojure.java.io :as io])

  (ImageIO/write bufimage "JPG" (io/file "test.jpg"))

  ;;Welcome the the land of useless error messages....That one happened because jpg
  ;;doesn't support the alpha channel...Remember the last dimension of our shape was
  ;;4

  (ImageIO/write bufimage "PNG" (io/file "test.png"))

  ;;Now in emacs, run M-x auto-image-file-mode`
  ;;Both stride and color are off.  First we fix stride.  It happened because
  ;;the tensor displays the order in row major format so we have:
  ;;[height width num-channels]
  ;;The buffered image constructor takes [width height].
  (def bufimage (BufferedImage. 640 480 BufferedImage/TYPE_4BYTE_ABGR))
  (def pixels (-> bufimage
                  (.getRaster)
                  (.getDataBuffer)
                  (.getData)))


  (def ignored (dtype/copy! tens 0 pixels 0 (dtype/ecount tens) {:unchecked? true}))

  (ImageIO/write bufimage "PNG" (io/file "test.png"))


  ;;Now we fix the ordering; the only 4 byte buffered image option is ABGR but the
  ;;original image is in RGBA so we have the transform RGBA->ABGR.  We accomplish
  ;;this with a tensor select call which is a subset of numpy's slice operator that
  ;;produces a view.  Copy reads the data out in linear row-major format into the
  ;;destination.
  (def ignored (-> (dtt/select tens :all :all [3 2 1 0])
                   (dtype/copy! 0 pixels 0
                                (dtype/ecount pixels)
                                {:unchecked? true})))

  (ImageIO/write bufimage "PNG" (io/file "test.png"))

  )
