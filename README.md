# JNA libpython bindings to Clojure.

### Version Info

[![Clojars Project](https://img.shields.io/clojars/v/clj-python/libpython-clj.svg)](https://clojars.org/clj-python/libpython-clj)
[![travis integration](https://travis-ci.com/clj-python/libpython-clj.svg?branch=master)](https://travis-ci.com/clj-python/libpython-clj)


 ## BETA 1 Is OUT!

 
 This brings JDK-16 support as well as 32 bit support using a brand new 
 [ffi interface](https://cnuernber.github.io/dtype-next/tech.v3.datatype.ffi.html) and
 removing about 1/2 of the existing code.  The library's API has not changed majorly 
 aside from deprecated methods have been removed.
 
 * Main namespace is now `libpython-clj2` -- docs have not been updated.
 * **32 bit support**!!
 * Partial support for JDK-16 - The tests pass for core Python functionality but numpy
   failed to load - the JDK-16 dynamic library loading path is different than JNA and of
   course this causes issues.
 * 20-30% better performance.
 * - Note - Please avoid deprecated versions such as `[cnuernber/libpython-clj "1.36"]` (***note name change***).
 * [API Documentation](https://clj-python.github.io/libpython-clj/)
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
            [tech.v2.datatype :as dtype]))



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


One very complimentary aspect of Python with respect to Clojure is it's integration
with cutting edge native libraries.  Our support isn't perfect so some understanding
of the mechanism is important to diagnose errors and issues.

Current, we launch the python3 executable and print out various different bits of
configuration as json.  We parse the json and use the output to attempt to find
the `libpython3.Xm.so` shared library so for example if we are loading python
3.6 we look for `libpython3.6m.so` on Linux or `libpython3.6m.dylib` on the Mac.

This pathway has allowed us support Conda albeit with some work.  For examples
using Conda, check out the facial rec repository a)bove or look into how we
[build](scripts/build-conda-docker)
our test [docker containers](dockerfiles/CondaDockerfile).

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
$ lein install
```

### Deploy to clojars

```bash
$ lein deploy clojars
```
This command will sign jar before deploy, using your gpg key. (see dev/src/build.clj for signing options)



## License

Copyright © 2019 Chris Nuernberger

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
