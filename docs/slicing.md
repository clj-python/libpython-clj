# Slicing And Slices


The way Python implements slicing is via overloading the `get-item` function call.
This is the call the Python interpreter makes under the covers whenever you use the
square bracket `[]` syntax.


For quite a few objects, that function call take a tuple of arguments. The trick to
numpy slicing is to create builtin slice objects with the appropriate arguments and
pass them into the `get-item` call in a tuple.


```clojure
user> (require '[libpython-clj.python :as py])
nil
user> (require '[libpython-clj.require :refer [require-python]])
... lotta logs ...
user> (require-python '[builtins])
WARNING: AssertionError already refers to: class java.lang.AssertionError in namespace: builtins, being replaced by: #'builtins/AssertionError
WARNING: Exception already refers to: class java.lang.Exception in namespace: builtins, being replaced by: #'builtins/Exception
:ok
user> (doc builtins/slice)
-------------------------
builtins/slice
[[self & [args {:as kwargs}]]]
  slice(stop)
slice(start, stop[, step])

Create a slice object.  This is used for extended slicing (e.g. a[0:10:2]).
nil
user> (require-python '[numpy :as np])
:ok

user> (require-python '[numpy :as np])
:ok
user> (def ary (-> (np/arange 9)
                   (np/reshape [3 3])))
#'user/ary
user> ary
[[0 1 2]
 [3 4 5]
 [6 7 8]]

user> (py/get-item ary [(builtins/slice 1 nil) (builtins/slice 1 nil)])
[[4 5]
 [7 8]]
user> (py/get-item ary [(builtins/slice -1) (builtins/slice 1 nil)])
[[1 2]
 [4 5]]
user> (py/get-item ary [(builtins/slice nil) (builtins/slice 1 nil)])
[[1 2]
 [4 5]
 [7 8]]
user> (py/get-item ary [(builtins/slice nil) (builtins/slice 1 2)])
[[1]
 [4]
 [7]]
user> (py/get-item ary [(builtins/slice nil) (builtins/slice 1 3)])
[[1 2]
 [4 5]
 [7 8]]
user> (py/get-item ary [(builtins/slice nil) (builtins/slice 1 4)])
[[1 2]
 [4 5]
 [7 8]]
```
