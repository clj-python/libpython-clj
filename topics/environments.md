# Python Environments

## pyenv

pyenv requires that you build the shared library.  This is a separate configuration option than a lot of pyenv users have used before.

* [pyenv shared library issue](https://github.com/pyenv/pyenv/issues/392)
* [libpython-clj related issue](https://github.com/clj-python/libpython-clj/issues/123)


## Conda

Conda requires that we set the LD_LIBRARY_PATH to the conda install.

* [example conda repl launcher](https://github.com/clj-python/libpython-clj/blob/master/scripts/conda-repl)
* [libpython-clj issue for Conda](https://github.com/clj-python/libpython-clj/issues/18)



## uv

When you are using the (awesome !) python package manager [uv](https://docs.astral.sh/uv/) we provide a nice integration, which allows to auto-mange 
declarative python environments.

Assuming that you have 'uv' installed (it exists for Linux, Windows , Mac) you can specify and auto-setup a local python venv incl. python version by adding the following to `python.edn`
(Linux example)

```
:python-version "3.10.16"
:python-deps ["openai==1.58.1"
              "langextract"]
:python-executable ".venv/bin/python"
:pre-initialize-fn libpython-clj2.python.uv/sync-python-setup!
```

The versions specification takes the same values as in uv, so would allow ranges a swell, for examples. We suggest to use precise versions, if possible

Having this, on  a call to `(py/initialize!)` a python venv will be created/updated to match the python version and the specified packages. This calls behind the scenes `uv sync` so the spec and the venv are "brought in sync". 

Re-syncing can be as well called manually (while the Clojure repl runs), invoking directly `(libpython-clj2.python.uv/sync-python-setup!)`

### ux on Windows

On Windows we need to use:
`:python-executable ".venv/Scripts/python"`

as the python executable.

### Caveat

We have noticed that under Windows for some python versions  `libpython-clj` does not setup the python library path correctly, resulting in python libraries not found using for example: `(py/import-module "xxx")`

This is visible by inspecting python `sys.path`, which should contain `.venv/` via `(py/run-simple-string "import sys; print(sys.path)")`,

`sys.path` should contain something like
`c:\\Users\\behrica\\Repos\\tryLangExtract\\.venv`

This can be fixed as by running after `(py/initialize!)` the following:

Windows: `(py/run-simple-string "import sys; sys.path.append('.venv/Lib/site-packages')")`
Linux: `(py/run-simple-string "import sys; sys.path.append('/.venv/lib/<python_version>/site-packages')")`

Not sure, if the precise paths can change across python versions. They can be discovered by looking into `.venv` directory and see where precisely the "site-packages" directory is located.