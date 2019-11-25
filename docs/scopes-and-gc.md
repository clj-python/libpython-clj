# Scopes And Garbage Collection


libpython-clj now supports stack-based scoping rules so you can guarantee all python
objects created during a section of code will be released by a certain point.


Using the stack-based scoping looks like:



```clojure
user> (require '[libpython-clj.python :as py])
nil
user> (py/initialize!)
... (logging elided)
:ok
user> (py/stack-resource-context
       (-> (py/->py-dict {:a 1 :b 2})
           ;;Note - Without this call you guarantee a crash.
           (py/->jvm)))
{"a" 1, "b" 2}
```


You must either call ->jvm or return a keyword at the end of your scope.

In the case where you are processing a batch of items (which we recommend for perf
reasons), you can also grab the GIL at the top of your thing:

```clojure
user> (def dict-seq (py/as-jvm (py/->py-list (repeat 1000 (py/->py-dict {:a 1 :b 2})))))
#'user/dict-seq


user> (def ignored (time (mapv py/->jvm dict-seq)))
"Elapsed time: 2200.556506 msecs"
#'user/ignored
user> (def ignored (time (py/with-gil (mapv py/->jvm dict-seq))))
"Elapsed time: 2095.815518 msecs"
#'user/ignored
```


The hidden thing above, regardless of if you grab the gil or not is that you are
actually holding onto a lot of python objects that could be released.  Hence if you
aren't disciplined about calling System/gc or if the jvm gc just decides not to run
you could be allocating a lot of native-heap objects.  Plus what you don't see is
that if you call System/gc the resource thread dedicated to releasing things will
have a lot of work to do.

For production use cases where you need a bit more assurance that things get released,
please consider both grabbing the gil *and* opening a resource context:


```clojure
user> (def ignored (time (py/with-gil-stack-rc-context
                           (->> (repeatedly 1000 #(py/->py-dict {:a 1 :b 2}))
                                (py/->py-list)
                                (py/as-jvm)
                                (mapv py/->jvm)))))

"Elapsed time: 3246.847595 msecs"
#'user/ignored
```

This took a second longer!  But, you *know* that all python objects allocated within
that scope are released.  Before, you would be in essence hoping that things would be
released soon enough.

Again, for production contexts we recommend batch processing objects *and* using
the `with-gil-stack-rc-context` function call that correctly grabs the gil, opens
a resource context and then releases anything allocated within that context.
