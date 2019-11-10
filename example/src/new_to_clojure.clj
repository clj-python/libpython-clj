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

  (defn gen123plus
    [x]
    (map #(+ x %) [1 2 3]))

  (interleave (gen123plus 10) (gen123plus 20))

  (defn zigzag
    [period]
    (->> (concat (range period)
                 (range period, 0 -1))
         repeat
         (apply concat)))

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
    [x]
    (py/call-attr-kw plt "plot" [x x] {"label" "linear"})
    (py/call-attr-kw plt "plot" [x (py/call-attr x "__pow__" 2)]
                     {"label" "quadratic"})
    (py/call-attr-kw plt "plot" [x (py/call-attr x "__pow__" 3)]
                     {"label" "cubic"})
    (py/call-attr plt "xlabel" "x label")
    (py/call-attr plt "ylabel" "y label")
    (py/call-attr plt "title" "Simple Plot")
    (py/call-attr plt "legend"))

  (plot-it x)

  (py/call-attr agg-canvas "draw")
  (def np-data (py/call-attr np "array"
                             (py/call-attr agg-canvas "buffer_rgba")))
  (def tens (py/as-tensor np-data))

  (require '[tech.v2.tensor :as dtt])
  (require '[tech.libs.buffered-image :as bufimg])

  (def jvm-img (bufimg/new-image 480 640 :byte-abgr))

  ;;Copy will read the data out in row-major format.
  (def ignored (dtype/copy! tens jvm-img))

  (bufimg/save! jvm-img "test.png")

  ;;Now in emacs, run M-x auto-image-file-mode`
  ;;The color of the image is off.  The reason is that the canvas produces an
  ;;"RGBA" image and java buffered image is ABGR.
  ;;The fix is to create a view of the tensor that has the last dimension reversed:

  (def tens-view (dtt/select tens :all :all [3 2 1 0]))

  (def ignored (dtype/copy! tens-view jvm-img))

  (bufimg/save! jvm-img "test.png")

  ;;now the image will be correct.
  )
