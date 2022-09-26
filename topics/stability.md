
Using `libpython-clj` is a different python runtime, in which some python libraries are not behaving as expcted or are not testet as extensively 
as using cpython.

In case we observe "hanging" or "JVM crashes" using `libpython-clj`
we can improve the stability of certain libraries by trying 4 different things, even combining them:


1. Use embeded mode
2. Use manual GIL management
3. Disable or change configuration, if possible, of the multiprocessing of a python library, if unstable
4. Use the resource contexts (??)

One example for using 1,2 and 3 is here:
https://github.com/behrica/libpython-clj-219
