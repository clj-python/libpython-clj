(ns libpython-clj2.python.class
  "Namespace to help create a new python class from Clojure.  Used as a core
  implementation technique for bridging JVM objects into python."
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.base :as py-base]
            [libpython-clj2.python.jvm-handle :as jvm-handle]
            [libpython-clj2.python.fn :as py-fn]
            [libpython-clj2.python.protocols :as py-proto]
            [tech.v3.datatype.errors :as errors])
  (:import [clojure.lang IFn]))


(defn py-fn->instance-fn
  "Given a python callable, return an instance function meant to be used
  in class definitions."
  [py-fn]
  (py-ffi/check-gil)
  (let [retval (-> (py-ffi/PyInstanceMethod_New py-fn)
                   (py-ffi/track-pyobject))]
    retval))


(defn make-tuple-instance-fn
  "Make an instance function - a function which will be passed the 'this' object as
  it's first argument.  In this case the default behavior is to
  pass raw python object ptr args to the clojure function without marshalling
  as that can add confusion and unnecessary overhead.  Self will be the first argument.
  Callers can change this behavior by setting the 'arg-converter' option as in
  'make-tuple-fn'.

  See options to [[libpython-clj2.python/make-callable]]."
  ([clj-fn & [{:keys [arg-converter]
               :or {arg-converter py-base/as-jvm}
               :as options}]]
   (py-ffi/with-gil
     ;;Explicity set arg-converter to override make-tuple-fn's default
     ;;->jvm arg-converter.
     (-> (py-fn/make-tuple-fn clj-fn (assoc options :arg-converter arg-converter))
         ;;Mark this as an instance function.
         (py-fn->instance-fn)))))


(defn make-kw-instance-fn
  "Make an instance function - a function which will be passed the 'this' object as
  it's first argument.  In this case the default behavior is to
  pass as-jvm bridged python object ptr args and kw dict args to the clojure function without
  marshalling.  Self will be the first argument of the arg vector.

  See options to [[libpython-clj2.python/make-callable]].

  Options:

  * `:arg-converter` - gets one argument and must convert into jvm space - defaults to as-jvm.
  * `:result-converter` - gets one argument and must convert to python space.
     Has reasonable default."
  ([clj-fn & [{:keys [arg-converter
                      result-converter]
               :or {arg-converter py-base/as-jvm}
               :as options}]]
   (let [options (assoc options :arg-converter arg-converter)
         result-converter (or result-converter #(py-fn/bridged-fn-arg->python % options))]
     (py-ffi/with-gil
       ;;Explicity set arg-converter to override make-tuple-fn's default
       ;;->jvm arg-converter.
       (-> (py-fn/make-kw-fn clj-fn (assoc options :result-converter result-converter))
           ;;Mark this as an instance function.
           (py-fn->instance-fn))))))


(defn create-class
  "Create a new class object.  Any callable values in the cls-hashmap
  will be presented as instance methods.
  Things in the cls hashmap had better be either atoms or already converted
  python objects.  You may get surprised otherwise; you have been warned.
  See the classes-test file in test/libpython-clj"
  [name bases cls-hashmap]
  (py-ffi/with-gil
    (py-ffi/with-decref
      [cls-dict (py-ffi/untracked-dict cls-hashmap py-base/->python)
       bases (py-ffi/untracked-tuple bases py-base/->python)]
      (-> (py-fn/call (py-ffi/py-type-type) name bases cls-dict)
          (py-base/as-jvm)))))


(def ^:private wrapped-jvm-destructor*
  (jvm-handle/py-global-delay
   (make-tuple-instance-fn
    (fn [self]
      (let [jvm-hdl (jvm-handle/py-self->jvm-handle self)]
        #_(log/debugf "Deleting bridged handle %d" jvm-hdl)
        (jvm-handle/remove-jvm-object jvm-hdl)
        nil)))))


(defn ^:no-doc wrapped-jvm-destructor
  []
  @wrapped-jvm-destructor*)


(def ^:private wrapped-jvm-constructor*
  (jvm-handle/py-global-delay
   (make-tuple-instance-fn jvm-handle/py-self-set-jvm-handle!)))


(defn ^:no-doc wrapped-jvm-constructor
  []
  @wrapped-jvm-constructor*)


(def ^:no-doc abc-callable-type*
  (jvm-handle/py-global-delay
   (py-ffi/with-decref [mod (py-ffi/PyImport_ImportModule "collections.abc")]
     (py-proto/get-attr mod "Callable"))))


(def ^:no-doc wrapped-fn-class*
  (jvm-handle/py-global-delay
     (create-class
      "LibPythonCLJWrappedFn" [@abc-callable-type*]
      {"__init__" (wrapped-jvm-constructor)
       "__del__" (wrapped-jvm-destructor)
       "__call__" (make-tuple-instance-fn
                   (fn [self & args]
                     (let [jvm-obj (jvm-handle/py-self->jvm-obj self)]
                       (-> (apply jvm-obj (map py-base/as-jvm args))
                           (py-ffi/untracked->python py-base/as-python)))))
       "__str__" (make-tuple-instance-fn
                  (fn [self]
                    (format
                     "libpython-clj-wrapper[%s]"
                     (.toString (jvm-handle/py-self->jvm-obj self)))))})))


(defn ^:no-doc wrap-ifn
  [ifn]
  (errors/when-not-errorf
   (instance? IFn ifn)
   "Object %s is not an instance of clojure.lang.IFn" ifn)
  (@wrapped-fn-class* (jvm-handle/make-jvm-object-handle ifn)))



(comment
  (def cls-obj*
)
  (@cls-obj* (jvm-handle/make-jvm-object-handle
              #(println "in python:" %)))


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
                 "__del__" (wrapped-jvm-destructor)
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
