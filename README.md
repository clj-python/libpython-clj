# Deep Clojure/Python Integration

### Version Info

[![Clojars Project](https://img.shields.io/clojars/v/clj-python/libpython-clj.svg)](https://clojars.org/clj-python/libpython-clj)
[![travis integration](https://travis-ci.com/clj-python/libpython-clj.svg?branch=master)](https://travis-ci.com/clj-python/libpython-clj)


 ## New Versions Are HOT!  Huge New Features! You Can't Afford To Miss Out!
 
 * [API Documentation](https://clj-python.github.io/libpython-clj/)
 * [Java API](https://clj-python.github.io/libpython-clj/libpython-clj2.java-api.html) - you can use libpython-clj from java - no
   Clojure required.  The class is included with the jar so just put the jar on the classpath and then `import libpython_clj2.java_api;`
   will work.  Be sure to carefully read the namespace doc as, due to performance considerations, not all methods are 
   protected via automatic GIL management.  Note this integration includes support for extremely efficient data copies to numpy objects
   and callbacks from python to java.
 * [make-fastcallable](https://clj-python.github.io/libpython-clj/libpython-clj2.python.html#var-make-fastcallable) so if you 
   calling a small function repeatedly you can now call it about twice as fast.  A better optimization is to call
   a function once with numpy array arguments but unfortunately not all use cases are amenable to this pathway.  So we
   did what we can.
 * JDK-17 and Mac M-1 support.  To use libpython-clj2 with jdk-17 you need to enable the foreign module -
   see [deps.edn](https://github.com/clj-python/libpython-clj/blob/6e7368b44aaabddf565a5bbf3a240e60bf3dcbf8/deps.edn#L10)
   for a working alias.
 * You can now [Embed Clojure in Python](https://clj-python.github.io/libpython-clj/embedded.html) - you can launch a Clojure REPL from a Python host process.
 * **32 bit support**!!
 * 20-30% better performance.
 * Please avoid deprecated versions such as `[cnuernber/libpython-clj "1.36"]` (***note name change***).
 * This library, which has received the efforts of many excellent people, is built mainly upon
   [cnuernber/dtype-next](https://github.com/cnuernber/dtype-next/) and the
   [JNA library](https://github.com/java-native-access/jna).
 * [Static code generation](https://clj-python.github.io/libpython-clj/libpython-clj2.codegen.html#var-write-namespace.21) - generate clojure namespaces
   wrapping python modules that are safe to use with AOT and load much faster than analogous `require-python` calls.  These namespace will not
   automatically initialize the python subsystem -- initialize! must be called first (or a nice exception is throw).


## libpython-clj features

* Bridge between JVM objects and Python objects easily; use Python in your Java and
  use some Java in your Python.
* Python objects are linked to the JVM GC such that when they are no longer reachable
  from the JVM their references are released.  Scope based resource contexts are
  [also available](topics/scopes-and-gc.md).
* Finding the python libraries is done dynamically allowing one system to run on multiple versions
  of python.
* REPL oriented design means fast, smooth, iterative development.
* Carin Meier has written excellent posts on [plotting](http://gigasquidsoftware.com/blog/2020/01/18/parens-for-pyplot/) and
  [advanced text generation](http://gigasquidsoftware.com/blog/2020/01/10/hugging-face-gpt-with-clojure/). She also has some
  great [examples](https://github.com/gigasquid/libpython-clj-examples).


## Vision

We aim to integrate Python into Clojure at a deep level.  This means that we want to
be able to load/use python modules almost as if they were Clojure namespaces.  We
also want to be able to use Clojure to extend Python objects.  I gave a
[talk at Clojure Conj 2019](https://www.youtube.com/watch?v=vQPW16_jixs) that
outlines more of what is going on.

This code is a concrete example that generates an
[embedding for faces](https://github.com/cnuernber/facial-rec):

```clojure
(ns facial-rec.face-feature
  (:require [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.-] :as py]
            [tech.v3.datatype :as dtype]))



(require-python 'mxnet
                '(mxnet ndarray module io model))
(require-python 'cv2)
(require-python '[numpy :as np])


(defn load-model
  [& {:keys [model-path checkpoint]
      :or {model-path "models/recognition/model"
           checkpoint 0}}]
  (let [[sym arg-params aux-params] (mxnet.model/load_checkpoint model-path checkpoint)
        all-layers (py. sym get_internals)
        target-layer (py/get-item all-layers "fc1_output")
        model (mxnet.module/Module :symbol target-layer
                                   :context (mxnet/cpu)
                                   :label_names nil)]
    (py. model bind :data_shapes [["data" [1 3 112 112]]])
    (py. model set_params arg-params aux-params)
    model))

(defonce model (load-model))


(defn face->feature
  [img-path]
  (py/with-gil-stack-rc-context
    (if-let [new-img (cv2/imread img-path)]
      (let [new-img (cv2/cvtColor new-img cv2/COLOR_BGR2RGB)
            new-img (np/transpose new-img [2 0 1])
            input-blob (np/expand_dims new-img :axis 0)
            data (mxnet.ndarray/array input-blob)
            batch (mxnet.io/DataBatch :data [data])]
        (py. model forward batch :is_train false)
        (-> (py. model get_outputs)
            first
            (py. asnumpy)
            (#(dtype/make-container :java-array :float32 %))))
      (throw (Exception. (format "Failed to load img: %s" img-path))))))
```


## Usage

#### Config namespace
```clojure
(ns my-py-clj.config
  (:require [libpython-clj2.python :as py]))

;; When you use conda, it should look like this.
(py/initialize! :python-executable "/opt/anaconda3/envs/my_env/bin/python3.7"
                :library-path "/opt/anaconda3/envs/my_env/lib/libpython3.7m.dylib")
```

#### Update project.clj
```clojure
{...
  ;; This namespace going to run when the REPL is up.
  :repl-options {:init-ns my-py-clj.config}
...}
```


```clojure
user> (require '[libpython-clj2.require :refer [require-python]])
...logging info....
nil
user> (require-python '[numpy :as np])
nil
user> (def test-ary (np/array [[1 2][3 4]]))
#'user/test-ary
user> test-ary
[[1 2]
 [3 4]]
```

We have a [document](topics/Usage.md) on all the features but beginning usage is
pretty simple.  Import your modules, use the things from Clojure.  We have put
effort into making sure things like sequences and ranges transfer between the two
languages.


#### Environments


One very complimentary aspect of Python with respect to Clojure is its integration
with cutting edge native libraries.  Our support isn't perfect so some understanding
of the mechanism is important to diagnose errors and issues.

Current, we launch the python3 executable and print out various different bits of
configuration as json.  We parse the json and use the output to attempt to find
the `libpython3.Xm.so` shared library so for example if we are loading python
3.6 we look for `libpython3.6m.so` on Linux or `libpython3.6m.dylib` on the Mac.

If we are unable to find a dynamic library such as `libpythonx.y.so` or `libpythonx.z.dylib`, 
it may be because Python is statically linked and the library is not present at all. 
This is dependent on the operating system and installation, and it is not always possible to detect it. 
In this case, we will receive an error message saying "Failed to find a valid python library!". 
To fix this, you may need to install additional OS packages or manually set the precise library location during `py/initialize!`.


This pathway has allowed us support Conda albeit with some work.  For examples
using Conda, check out the facial rec repository a)bove or look into how we
[build](scripts/build-conda-docker)
our test [docker containers](dockerfiles/CondaDockerfile).

#### devcontainer 
The scicloj community is maintaining a `devcontainer` [template](https://github.com/scicloj/devcontainer-templates/tree/main/src/scicloj) on which `libpython-clj` is know to work
out of the box.

This can be used as a starting point for projects using `libpython-clj` or as reference for debuging issues.


## Community

We like to talk about libpython-clj on [Zulip](https://clojurians.zulipchat.com/#streams/215609/libpython-clj-dev) as the conversations are persistent and searchable.


## Further Information

* Clojure Conj 2019 [video](https://www.youtube.com/watch?v=vQPW16_jixs) and
  [slides](https://docs.google.com/presentation/d/1uegYhpS6P2AtEfhpg6PlgBmTSIPqCXvFTWcGYG_Qk2o/edit?usp=sharing).
* [development discussion forum](https://clojurians.zulipchat.com/#narrow/stream/215609-libpython-clj-dev)
* [design documentation](topics/design.md)
* [scope and garbage collection docs](topics/scopes-and-gc.md)
* [examples](https://github.com/gigasquid/libpython-clj-examples)
* [docker setup](https://github.com/scicloj/docker-hub)
* [pandas bindings (!!)](https://github.com/alanmarazzi/panthera)
* [nextjournal notebooks](https://nextjournal.com/kommen)
* [scicloj video](https://www.youtube.com/watch?v=ajDiGS73i2o)
* [Clojure/Python interop technical blog post](http://techascent.com/blog/functions-across-languages.html)
* [persistent datastructures in python](https://github.com/tobgu/pyrsistent)


## New To Clojure

New to Clojure or the JVM?  Try remixing the nextjournal entry and playing around
there.  For more resources on learning and getting more comfortable with Clojure,
we have an [introductory document](topics/new-to-clojure.md).


## Resources

* [libpython C api](https://docs.python.org/3.7/c-api/index.html#c-api-index)
* [create numpy from C ptr](https://stackoverflow.com/questions/23930671/how-to-create-n-dim-numpy-array-from-a-pointer)
* [create C ptr from numpy](https://docs.scipy.org/doc/numpy/reference/generated/numpy.ndarray.ctypes.html)


To install jar to local .m2 :

```bash
$ clj -X:depstar
```

After building and process `pom.xml` file you can run:

```bash
$ clj -X:install
```

### Deploy to clojars

```bash
$ clj -X:deploy
```
> This command will sign jar before deploy, using your gpg key.


## License

Copyright © 2019 Chris Nuernberger

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
