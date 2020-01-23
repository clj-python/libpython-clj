(ns libpython-clj.python-test
  (:require [libpython-clj.python :as py :refer [py. py.. py.- py* py**]]
            [libpython-clj.jna :as libpy]
            [tech.v2.datatype :as dtype]
            [tech.v2.datatype.functional :as dfn]
            [tech.v2.tensor :as dtt]
            [tech.jna :as jna]
            [clojure.test :refer :all])
  (:import [java.io StringWriter]
           [java.util Map List]
           [com.sun.jna Pointer]))

(py/initialize!)

(deftest stdout-and-stderr
  (is (= "hey\n" (with-out-str
                   (py/run-simple-string "print('hey')"))))
  (is (= "hey\n" (let [custom-writer (StringWriter.)]
                   (with-bindings {#'*err* custom-writer}
                     (py/run-simple-string "import sys\nprint('hey', file=sys.stderr)"))
                   (.toString custom-writer))))
  ;;Python exceptions get translated into actual java exceptions.
  (is (thrown? Throwable (py/run-simple-string "import sys\nprint('hey', stderr"))))

(deftest dicts
  (let [py-dict (py/->python {:a 1 :b 2})]
    (is (= :dict (py/python-type py-dict)))
    (is (= 2 (-> (py/call-attr py-dict "__len__")
                 (py/->jvm))))
    (is (= {"a" 1 "b" 2}
           (->> (py/->jvm py-dict)
                (into {}))))
    (let [bridge-dict (py/as-jvm py-dict)]
      (is (instance? Map bridge-dict))
      (is (= #{"a" "b"} (->> (keys bridge-dict)
                             set)))
      (is (= #{1 2} (->> (vals bridge-dict)
                         set)))
      (is (= {"a" 1 "b" 2}
             (into {} bridge-dict))))))

(deftest lists
  (let [py-list (py/->py-list [4 3 2 1])]
    (is (= :list (py/python-type py-list)))
    (is (= 4 (-> (py/call-attr py-list "__len__")
                 (py/->jvm))))
    (is (= [4 3 2 1]
           (->> (py/->jvm py-list)
                (into []))))

    (let [bridge-list (py/as-jvm py-list)]
      (is (instance? List bridge-list))
      (is (= [4 3 2 1] (into [] bridge-list)))
      ;;This actually calls the python sort function.
      (.sort ^List bridge-list nil)
      (is (= [1 2 3 4] (into [] bridge-list))))))

(deftest global-dict
  (let [main-module (py/add-module "__main__")
        ^Map globals (-> (py/module-dict main-module)
                         (py/as-jvm))]
    (is (instance? Map globals))
    (.put globals "item" 100)
    (py/set-item! globals "item2" 200)
    ;;During run-simple-string, if nothing else is specified the
    ;;global map is used as a local map.
    (py/run-simple-string "item3 = item + item2")
    (is (= 300 (globals "item3")))))

(deftest numpy-and-back
  (let [jvm-tens (dtt/->tensor (->> (range 9)
                                    (partition 3)))]
    ;;zero-copy can't work on jvm datastructures with current JNA tech.
    ;;IT would require 'pinning' which isn't yet available.
    (is (nil? (py/as-numpy jvm-tens)))
    (let [py-tens (py/->numpy jvm-tens)]
      (is (= [[0.0 1.0 2.0] [3.0 4.0 5.0] [6.0 7.0 8.0]]
             (-> (py/as-tensor py-tens)
                 dtt/->jvm)))
      ;;This operation is in-place
      (let [py-trans (py/call-attr py-tens "transpose" [1 0])]
        (is (= [[0.0 1.0 2.0] [3.0 4.0 5.0] [6.0 7.0 8.0]]
               (-> (py/as-tensor py-tens)
                   dtt/->jvm)))
        (is (= [[0.0 3.0 6.0] [1.0 4.0 7.0] [2.0 5.0 8.0]]
               (-> (py/as-tensor py-trans)
                   dtt/->jvm)))
        ;;But they are sharing backing store, so mutation will travel both
        ;;ways.  Creepy action at a distance indeed
        (dtype/copy! [5 6 7] (py/as-tensor py-trans))
        (is (= [[5.0 1.0 2.0] [6.0 4.0 5.0] [7.0 7.0 8.0]]
               (-> (py/as-tensor py-tens)
                   dtt/->jvm))))
      (let [main-module (py/add-module "__main__")
            ^Map globals (-> (py/module-dict main-module)
                             (py/as-jvm))]
        (py/set-item! globals "np_ary" py-tens)
        (py/run-simple-string "np_ary[2,2] = 100")
        (is (= [[5.0 1.0 2.0] [6.0 4.0 5.0] [7.0 7.0 100.0]]
               ;;zero copy almost always works the other way, however.  So there is
               ;;py/->tensor available.  Copying the numpy object will allow the
               ;;zero copy pathway to work.
               (-> (py/as-tensor py-tens)
                   dtt/->jvm)))))))

(deftest numpy-scalars
  (let [np (py/import-module "numpy")
        scalar-constructors (concat ["float64"
                                     "float32"]
                                    (for [int-type ["int" "uint"]
                                          int-width [8 16 32 64]]
                                      (str int-type int-width)))]
    (doseq [constructor scalar-constructors]
      (is (= 3.0 (-> (py/call-attr np constructor 3.0)
                     double))
          (str "Item type " constructor)))))

(deftest dict-with-complex-key
  (let [py-dict (py/->python {["a" "b"] 1
                              ["c" "d"] 2})
        bridged (py/as-jvm py-dict)]
    (is (= #{["a" "b"]
             ["c" "d"]}
           (->> (keys bridged)
                ;;Bridged tuples are lists, not persistent vectors.
                (map vec)
                set)))))

(deftest simple-print-crashed
  (let [numpy (py/import-module "numpy")]
    (println (py/as-tensor (py/call-attr numpy "ones" [3 3])))))

(deftest true-false-list
  (is (= [false true]
         (-> '(false true)
             py/->py-list
             py/->jvm))))

(deftest true-false-true-numpy
  (let [numpy (py/import-module "numpy")]
    (is (= [true false true]
           (->> (for [a (py/call-attr numpy "array" [true false true])]
                  a)
                vec)))))

(deftest aspy-iter
  (let [testcode-module (py/import-module "testcode")]
    (is (= [1 2 3 4 5]
           (-> (py/call-attr testcode-module
                             "for_iter" (py/as-python [1 2 3 4 5]))
               (py/->jvm))))
    (is (= ["a" "b" "c"]
           (-> (py/call-attr testcode-module
                             "for_iter" (py/as-python {"a" 1 "b" 2 "c" 3}))
               (py/->jvm))))))

(deftest basic-with-test
  (let [testcode-module (py/import-module "testcode")]
    (let [fn-list (py/->py-list [])]
      (is (nil?
           (py/with [item (py/call-attr testcode-module "WithObjClass" true fn-list)]
                    (py/call-attr item "doit_err"))))
      (is (= ["enter" "exit: ('Spam', 'Eggs')"]
             (py/->jvm fn-list))))
    (let [fn-list (py/->py-list [])]
      (is (= 1
             (py/with [item (py/call-attr testcode-module "WithObjClass" true fn-list)]
                      (py/call-attr item "doit_noerr"))))
      (is (= ["enter" "exit: None"]
             (py/->jvm fn-list))))
    (let [fn-list (py/->py-list [])]
      (is (thrown? Throwable
                   (py/with [item (py/call-attr testcode-module "WithObjClass"
                                                false fn-list)]
                            (py/call-attr item "doit_err"))))
      (is (= ["enter" "exit: ('Spam', 'Eggs')"]
             (py/->jvm fn-list))))
    (let [fn-list (py/->py-list [])]
      (py/with [item (py/call-attr testcode-module "WithObjClass"
                                   false fn-list)]
               (py/call-attr item "doit_noerr"))
      (is (= ["enter" "exit: None"]
             (py/->jvm fn-list))))))

(deftest arrow-as-fns-with-nil
  (is (= nil (py/->jvm nil)))
  (is (= nil (py/as-jvm nil))))

(deftest pydict-nil-get
  (let [dict (py/->python {:a 1 :b {:a 1 :b 2}})
        bridged (py/as-jvm dict)]
    (is (= nil (bridged nil)))))

(deftest custom-clojure-item
  (let [att-map {"clojure_fn" (py/->python #(vector 1 2 3))}
        my-python-item (py/create-bridge-from-att-map
                        ;;First is the jvm object that this bridge stands for.  If this
                        ;;gets garbage collected then the python side will be removed
                        ;;also.
                        att-map
                        ;;second is the list of attributes.  In this case, since this
                        ;;object isn't iterable or anything, this function will do.
                        att-map)
        py-mod (py/import-module "testcode")]
    (is (= [1 2 3]
           (py/call-attr py-mod "calling_custom_clojure_fn" my-python-item)))

    ;;Now this case is harder.  Let's say we have something that is iterable and we
    ;;want this to be reflected in python.  In that case we have to call 'as-python'
    ;;on the iterator and that 'should' work.

    (let [my-obj (reify
                   Iterable
                   (iterator [this] (.iterator [4 5 6])))
          ;;Note that attributes themselves have to be python objects and wrapping
          ;;this with as-python makes that function wrap whatever it returns in a
          ;;bridging python object also.
          att-map {"__iter__" (py/as-python #(.iterator my-obj))}
          my-python-item (py/create-bridge-from-att-map
                          my-obj
                          ;;second is the list of attributes.  In this case, since this
                          ;;object isn't iterable or anything, this function will do.
                          att-map)]
      (is (= [4 5 6]
             (vec my-obj)))
      (is (= [4 5 6]
             (vec (py/call-attr py-mod "for_iter" my-python-item)))))))

(deftest bridged-dict-to-jvm
  (let [py-dict (py/->py-dict {:a 1 :b 2})
        bridged (py/as-jvm py-dict)
        copied-back (py/->jvm bridged)]
    (is (instance? clojure.lang.PersistentArrayMap copied-back))))

(deftest calling-conventions
  (let [np (py/import-module "numpy")
        linspace (py/$. np linspace)]

    (is (dfn/equals [2.000 2.250 2.500 2.750 3.000]
                    (py/as-tensor (linspace 2 3 :num 5))))
    (is (dfn/equals [2.000 2.250 2.500 2.750 3.000]
                    (py/as-tensor (py/c$ linspace 2 3 :num 5))))
    (is (dfn/equals [2.000 2.250 2.500 2.750 3.000]
                    (py/as-tensor (py/a$ np linspace 2 3 :num 5))))))

(deftest syntax-sugar
  (py/initialize!)
  (let [np (py/import-module "numpy")]
    (is (= (str (py/$. np linspace))
           (str (py/get-attr np "linspace"))))
    (is (= (str (py/$.. np random shuffle))
           (str (-> (py/get-attr np "random")
                    (py/get-attr "shuffle"))))))


  (let [builtins (py/import-module "builtins")
        l        (py/call-attr builtins "list")]
    (is (= (py/py. l __len__) 0))
    (py/py. l append 1)
    (is (= (py/py. l __len__) 1))
    (py/py.. l (extend [1 2 3]))
    (is (= ((py/py.- l __len__)) 4)))

  (let [sys (py/import-module "sys")]
    (is (int? (py/py.. sys -path __len__))))


  (let [{{Foo :Foo} :globals}
        (py/run-simple-string "
class Foo:

    def __init__(self, a, b):
        self.a = a
        self.b = b
        self._res = []

    def res(self):
        return self._res

    def extend(self, arg):
        self._res.extend(arg)
        return self

    def count(self):
        return len(self._res)

    def append(self, *args):
        return self.extend(args)


    def math(self, c):
        return self.append(self.a + self.b + c)

")
        f (Foo 1 2)]
    (is (= [] ((py/py.- f res)) (py/py. f res)))
    (is (= 6 (py/py.. f (append 1)
                      (append 2)
                      (math 3)
                      (extend [4 5 6]) count)))
    (is (= [1 2 6 4 5 6] ((py/py.- f res)) (py/py. f res)))
    (is (= 6
           (py/py.. f res __len__)
           ((py/py.. f res -__len__))
           (py/py.. f count)
           ((py/py.. f -count)))))


  (let [builtins (py/import-module "builtins")
        dict (py/get-attr builtins "dict")
        d (dict)]
    (py* d update nil {:a 1})
    (is  (= 1 (py* d get [:a])))
    (let [iterable [[:a 2]]]
      (py* d update [iterable]))
    (is (= 2 (py* d get [:a])))
    (let [iterable [[:a 1] [:b 2]]
          kwargs {:name "taco"}]
      (py* d update [iterable] kwargs))
    (is (= {"name" "taco" "a" 1 "b" 2} d))

    (py. d clear)

    (py** d update {:a 1})
    (is (= d {"a" 1}))

    (py** d update [[:a 2]] {:c 3})
    (is (= d {"a" 2 "c" 3}))


    (py. d clear)

    (doto d
      (py.. (*update nil {:a 1}))
      (py.. (*update [[[:b 2]]]))
      (py.. (**update {:c 3}))
      (py.. (**update [[:d 4]] {:e 5})))

    (is (= d {"a" 1 "b" 2 "c" 3 "d" 4 "e" 5})))
  )

(deftest infinite-seq
  (let [islice (-> (py/import-module "itertools")
                   (py/get-attr "islice"))]

    (is (= (vec (range 10))
           ;;Range is an infinite sequence
           (-> (range)
               (islice 0 10)
               (vec))))))

(deftest persistent-vector-nparray
  (testing "Create numpy array from nested persistent vectors"
    (let [ary-data (-> (py/import-module "numpy")
                       (py/$a array [[1 2 3]
                                     [4 5 6]]))]
      (is (dfn/equals (dtt/->tensor [[1 2 3]
                                     [4 5 6]])
                      (py/as-tensor ary-data))))))

(deftest python-tuple-equals
  (testing "Python tuples have nice equal semantics."
    (let [lhs (py/->py-tuple [1 2])
          same (py/->py-tuple [1 2])
          not-same (py/->py-tuple [3 4])]
      (is (= lhs same))
      (is (not= lhs not-same))
      (is (= (.hashCode lhs)
             (.hashCode same)))
      (is (not= (.hashCode lhs)
                (.hashCode not-same))))))

(deftest range-nparray
  (let [ary-data (-> (py/import-module "numpy")
                     (py/$a array (range 10)))]
    (is (dfn/equals (dtt/->tensor (range 10))
                    (py/as-tensor ary-data)))))

(deftest false-is-always-py-false
  (let [py-false (libpy/Py_False)
        ->false (py/->python false)
        as-false (py/as-python false)]
    (is (= (Pointer/nativeValue (jna/as-ptr py-false))
           (Pointer/nativeValue (jna/as-ptr ->false))))
    (is (= (Pointer/nativeValue (jna/as-ptr py-false))
           (Pointer/nativeValue (jna/as-ptr as-false))))))

(deftest instance-abc-classes
  (let [py-dict (py/->python {"a" 1 "b" 2})
        bridged-dict (py/as-python {"a" 1 "b" 2})
        bridged-iter (py/as-python (repeat 5 1))
        bridged-list (py/as-python (vec (range 10)))
        pycol (py/import-module "collections")
        mapping-type (py/get-attr pycol "Mapping")
        iter-type (py/get-attr pycol "Iterable")
        sequence-type (py/get-attr pycol "Sequence")]
    (is (py/is-instance? py-dict mapping-type))
    (is (py/is-instance? bridged-dict mapping-type))
    (is (py/is-instance? bridged-iter iter-type))
    (is (py/is-instance? bridged-list sequence-type))))

(deftest nested-map-and-back
  (let [py-dict (-> (py/run-simple-string "testdata={'camera_id': 'CODOT-10106-12067', 'country': 'United States', 'state': 'Colorado', 'city': 'Fountain', 'provider': 'CO DOT', 'description': '0.6 mi N of Ray Nixon Rd Int', 'direction': 'North', 'video': False, 'links': {'jpeg': {'url': 'https://www.cotrip.org/dimages/camera?imageURL=remote/CTMCCAM025S125-20-N.jpg'}}, 'tags': ['auto_rerated'], 'ratings': {'road_weather': 4, 'visibility': 4}, 'health': {}, 'created_at': '2016-04-19T20:27:45.030Z', 'time_zone_offset': -25200}")
                    (get-in [:globals "testdata"]))
        jvm-dict (py/->jvm py-dict)]
    (is (= (get jvm-dict "ratings")
           (py/->jvm (py/get-item py-dict "ratings"))))))

(deftest characters
  (is (= (py/->jvm (py/->python "c"))
         (py/->jvm (py/->python \c)))))


(comment
  (require '[libpython-clj.require :refer [require-python]])

  (require-python '[pandas :as pd])
  (require-python '[plotly.express :as px])
  (def px (py/import-module "plotly.express"))

  (let [data (doto (pd/DataFrame {:index [1 2] :value [2 3] :variable [1 1]})
               (py. melt :id_vars "index"))]
    (py. px line :data_frame data :x "index" :y "value" :color "variable"))


  (let [data (doto (pd/DataFrame {:index [1 2] :value [2 3] :variable [1 1]})
               (py. melt :id_vars "index"))]
    ((py.- px line) :data_frame data :x "index" :y "value" :color "variable"))

  )
