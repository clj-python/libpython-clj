# libpython-clj

libpython bindings to the tech ecosystem.


## Resources

* [libpython C api](https://docs.python.org/3.7/c-api/index.html#c-api-index)
* [spacy-cpp](https://github.com/d99kris/spacy-cpp)

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

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
