(ns walkthough)


(comment
  ;; First step is to initialize library
  (require '[libpython-clj.python :as py])
  (py/initialize!)

  ;;The system uses clojure.tools.logging.


  ;;'->' means copy, 'as' means bridge.

  (def test-dict (py/->python {:a 1 :b 2}))
  (py/python-type test-dict)

  ;;This created a 'dict' object in python and returned
  ;;a pointer to that object.  test-dict is a jna pointer.

  (py/att-type-map test-dict)

  (def bridged (py/as-jvm test-dict))

  ;;bridged implements java.util.Map and as such things like
  ;;count, get work.
  ;;to add an element, use the .put method.  Also iteration works
  ;;but you get back bridged objects.
  (def back-to-clojure (into {} bridged))


  ;;That is fun, and those are the basic mechanics of the language.
  ;;Now let's actually use something

  (def np (py/import-module "numpy"))

  ;;Lets lookup the function documentation for 'linspace':
  (-> (py/get-attr np "linspace")
      (py/get-attr "__doc__")
      print)

  ;;Examples
  ;;--------
  ;;>>> np.linspace(2.0, 3.0, num=5)
  (def np-ary (py/call-attr-kw np "linspace" [2.0 3.0] {:num 5}))
  )
