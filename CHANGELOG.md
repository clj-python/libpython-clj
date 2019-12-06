# Time for a ChangeLog!


## 1.16

Fixed a bug where the system would load multiple python libraries, not stopping
after the first valid library loaded.  There are two ways to control the system's
python library loading mechanism:

1. Pass in a library name in initialize!
2. alter-var-root the list of libraries in libpython-clj.jna.base before
   calling initialize!.


## 1.15

Moar syntax sugar --
```clojure
user> (py/$. numpy linspace)
<function linspace at 0x7fa6642766a8>
user> (py/$.. numpy random shuffle)
<built-in method shuffle of numpy.random.mtrand.RandomState object at 0x7fa66410cca8>
```


## 1.14

libpython-clj now searches for several shared libraries instead of being hardcoded
to just one of them.  Because of this, there is now:
```clojure
libpython-clj.jna.base/*python-library-names*
```

This is a sequence of library names that will be tried in order.

You can also pass in the desired library name as part of the initialize! call and
only this name will be tried.
