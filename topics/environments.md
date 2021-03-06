# Python Environments

## pyenv

pyenv requires that you build the shared library.  This is a separate configuration option than a lot of pyenv users have used before.

* [pyenv shared library issue](https://github.com/pyenv/pyenv/issues/392)
* [libpython-clj related issue](https://github.com/clj-python/libpython-clj/issues/123)


## Conda

Conda requires that we set the LD_LIBRARY_PATH to the conda install.

* [example conda repl launcher](https://github.com/clj-python/libpython-clj/blob/master/scripts/conda-repl)
* [libpython-clj issue for Conda](https://github.com/clj-python/libpython-clj/issues/18)
