# libpython-clj

jna libpython bindings to the tech ecosystem.

If you have a production need right now, I strongly advise you to consider
[jep](https://github.com/ninia/jep).

This project is mean to explore what is possible using JNA.  This has a lot of
advantages over a [10,000 line C binding layer](https://github.com/ninia/jep/tree/master/src/main/c)
...if it works.


## Resources

* [libpython C api](https://docs.python.org/3.7/c-api/index.html#c-api-index)
* [spacy-cpp](https://github.com/d99kris/spacy-cpp)
* [base classes for python protocols](https://docs.python.org/3.7/library/collections.abc.html#collections-abstract-base-classes)
* [create numpy from C ptr](https://stackoverflow.com/questions/23930671/how-to-create-n-dim-numpy-array-from-a-pointer)
* [create C ptr from numpy](https://docs.scipy.org/doc/numpy/reference/generated/numpy.ndarray.ctypes.html)


## Usage

```console
sudo apt install libpython3.7-dev
```

```clojure
(deftest print-test
  (libpy/Py_InitializeEx 0)
  (libpy/PyRun_SimpleString
"from time import time,ctime
print('Today is', ctime(time()))
")
  (let [finalize-val (libpy/Py_FinalizeEx)]
    (println finalize-val)))
```

```console
chrisn@chrisn-lt-2:~/dev/cnuernber/clj-libpython$ lein test
:tech.resource.gc Reference thread starting

lein test libpython-clj.jna-test
Library python3.7m found at [:system "python3.7m"]
Today is Thu May 16 14:26:21 2019
0

Ran 1 tests containing 0 assertions.
0 failures, 0 errors.
```

## License

Copyright © 2019 Chris Nuernberger

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
