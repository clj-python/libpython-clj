# libpython-clj

JNA libpython bindings to the tech ecosystem.


* The exact same binary can run top of on multiple version of python reducing version
  dependency chain management issues.
* Development of new functionality is faster because it can be done from purely from the
  REPL.


## Usage

```console
sudo apt install libpython3.6-dev
pip3 install numpy
```

```clojure
user>
user> (require '[libpython-clj.python
                 :refer [as-python as-jvm
                         ->python ->jvm
                         attr call-attr call-attr-kw
                         item att-type-map
                         call call-kw initialize!]])
:tech.resource.gc Reference thread starting
nil

user> (require '[libpython-clj.python :as python])
:tech.resource.gc Reference thread starting
nil
user> (python/initialize!)
info: executing python initialize!
Library python3.6m found at [:system "python3.6m"]
:ok

user> (python/run-simple-string "print('hey')")
hey
{:globals #object[com.sun.jna.Pointer 0x5d583373 "native@0x7ff0dc04f3a8"],
 :locals #object[com.sun.jna.Pointer 0x5d583373 "native@0x7ff0dc04f3a8"],
 :result #object[com.sun.jna.Pointer 0x86d7ae5 "native@0x7ff0d6a6c150"]}

user> (python/run-simple-string "print('syntax-errrrrr")
Execution error (ExceptionInfo) at libpython-clj.python.interpreter/check-error-throw (interpreter.clj:260).
  File "<string>", line 1
    print('syntax-errrrrr
                        ^
SyntaxError: EOL while scanning string literal


user> (python/run-simple-string "item = 10")
{:globals #object[com.sun.jna.Pointer 0x55418aaa "native@0x7ff0dc04f3a8"],
 :locals #object[com.sun.jna.Pointer 0x55418aaa "native@0x7ff0dc04f3a8"],
 :result #object[com.sun.jna.Pointer 0x52f3ccc9 "native@0x7ff0d6a6c150"]}

user> (def global-map (python/python->jvm (:globals *1)))
#'user/global-map
user> (keys global-map)
("__name__"
 "__doc__"
 "__package__"
 "__loader__"
 "__spec__"
 "__annotations__"
 "__builtins__"
 "item")
user> (get global-map "item")
10
user> (.put global-map "item" 100)
100

user> (python/run-simple-string "print('' + item)")
Execution error (ExceptionInfo) at libpython-clj.python.interpreter/check-error-throw (interpreter.clj:260).
Traceback (most recent call last):
  File "<string>", line 1, in <module>
TypeError: must be str, not int

user> (python/run-simple-string "print('' + str(item))")
100
{:globals #object[com.sun.jna.Pointer 0x52eee652 "native@0x7ff0dc04f3a8"],
 :locals #object[com.sun.jna.Pointer 0x52eee652 "native@0x7ff0dc04f3a8"],
 :result #object[com.sun.jna.Pointer 0x19e9d429 "native@0x7ff0d6a6c150"]}

;;  THIS SYNTAX IS CHANGING  !!!!;;
;;  The -> operator is going to be a copy operator while the
;;  as operator is going to be bridge, so  as-jvm instead of python->jvm

user> (def numpy (-> (python/import-module "numpy")
                     (python/python->jvm)))
#'user/numpy
user> (def ones-fn (get numpy "ones"))
#'user/ones-fn
user> (def ary-data (ones-fn [2 3]))
#'user/ary-data
user> (get ary-data "shape")
[2 3]

user> (def ary-ctype (get ary-data "ctypes"))
#'user/ary-ctype
user> (def test-ptr-val (get ary-ctype "data"))
#'user/test-ptr-val
user> test-ptr-val
140670943595536

user> (require '[tech.v2.datatype.jna :as dtype-jna])
nil
user> (def zero-copy-raw-data (dtype-jna/unsafe-address->typed-pointer test-ptr-val (* 8 6) :float64))
#'user/zero-copy-raw-data
user> zero-copy-raw-data
#object[java.nio.DirectDoubleBufferU 0x50beba84 "java.nio.DirectDoubleBufferU[pos=0 lim=6 cap=6]"]
user> (require '[tech.v2.datatype :as dtype])
nil
user> (dtype/->vector zero-copy-raw-data)
[1.0 1.0 1.0 1.0 1.0 1.0]
```


## Resources

* [libpython C api](https://docs.python.org/3.7/c-api/index.html#c-api-index)
* [create numpy from C ptr](https://stackoverflow.com/questions/23930671/how-to-create-n-dim-numpy-array-from-a-pointer)
* [create C ptr from numpy](https://docs.scipy.org/doc/numpy/reference/generated/numpy.ndarray.ctypes.html)



## License

Copyright © 2019 Chris Nuernberger

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
