(ns libpython-clj2.python.class
  "Namespace to help create a python class from Clojure."
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.copy :as py-copy]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.fn :as py-fn]
            [libpython-clj2.python.protocols :as py-proto]))


(defn- py-fn->instance-fn
  "Given a python callable, return an instance function meant to be used
  in class definitions."
  [py-fn]
  (py-ffi/check-gil)
  (-> (py-ffi/PyInstanceMethod_New py-fn)
      (py-ffi/wrap-pyobject)))


(defn make-tuple-instance-fn
  "Make an instance function - a function which will be passed the 'this' object as
  it's first argument.  In this case the default behavior is to
  pass raw python object ptr args to the clojure function without marshalling
  as that can add confusion and unnecessary overhead.  Self will be the first argument.
  Callers can change this behavior by setting the 'arg-converter' option as in
  'make-tuple-fn'.
  Options are the same as make-tuple-fn."
  ([clj-fn & [{:keys [arg-converter]
               :or {arg-converter identity}
               :as options}]]
   (py-ffi/with-gil
     ;;Explicity set arg-converter to override make-tuple-fn's default
     ;;->jvm arg-converter.
     (-> (py-fn/make-tuple-fn clj-fn (assoc options :arg-converter arg-converter))
         ;;Mark this as an instance function.
         (py-fn->instance-fn)))))


(defn create-class
  "Create a new class object.  Any callable values in the cls-hashmap
  will be presented as instance methods.
  Things in the cls hashmap had better be either atoms or already converted
  python objects.  You may get surprised otherwise; you have been warned.
  See the classes-test file in test/libpython-clj"
  [name bases cls-hashmap]
  (py-ffi/with-gil
    (py-ffi/check-error-throw)
    (let [cls-dict (reduce (fn [cls-dict [k v]]
                             (py-proto/set-item! cls-dict k (py-base/->python v))
                             cls-dict)
                           (py-base/->python {})
                           cls-hashmap)
          bases (py-copy/->py-tuple bases)
          new-cls (py-fn/call (py-ffi/py-type-type) name bases cls-dict)]
      (py-proto/as-jvm new-cls nil))))


(comment
  (def cls-obj (create-class
                "Stock" nil
                {"__init__" (make-tuple-instance-fn
                             (fn init [self name shares price]
                               ;;Because we did not use an arg-converter, all the
                               ;;arguments above are raw jna Pointers - borrowed
                               ;;references.
                               (py-proto/set-attr! self "name" name)
                               (py-proto/set-attr! self "shares" shares)
                               (py-proto/set-attr! self "price" price)
                                ;;If you don't return nil from __init__ that is an
                                ;;error.
                               nil))
                 "cost" (make-tuple-instance-fn
                         (fn cost [self]
                           (* (py-proto/get-attr self "shares")
                              (py-proto/get-attr self "price")))
                          ;;Convert self to something that auto-marshals things.
                          ;;This pathway will autoconvert all arguments to the function.
                         {:arg-converter py-base/as-jvm})
                  "__str__" (make-tuple-instance-fn
                             (fn str [self]
                               ;;Alternative to using arg-converter.  This way you can
                               ;;explicitly control which arguments are converted.
                               (let [self (py-base/as-jvm self)]
                                 (pr-str {"name" (py-proto/get-attr self "name")
                                          "shares" (py-proto/get-attr self "shares")
                                          "price" (py-proto/get-attr self "price")}))))
                 "clsattr" 55}))

  (def inst (cls-obj "ACME" 50 90))
  (py-fn/call-attr inst "cost")
  )
