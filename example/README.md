# libpython-clj Examples


## Usage

```console
scripts/get-data.sh
python3.6 -m pip install tensorflow keras matplotlib --user
```

Then, potentially check that everything is installed.  From python console:

```console
Python 3.6.7 (default, Oct 22 2018, 11:32:17)
[GCC 8.2.0] on linux
Type "help", "copyright", "credits" or "license" for more information.
>>> import keras
Using TensorFlow backend.
>>>
```

## Examples


* [walkthough](src/walkthough.clj) - Simple, quick exploration of libpython features.
* [keras-simple](src/keras_simple.clj) - Keras example taken from [here](https://machinelearningmastery.com/tutorial-first-neural-network-python-keras/).
* [matplotlib](src/matplotlib.clj) - Quick demo of matplotlib, numpy including zero-copy pathways.


## Virtual Environments


This was lightly tested with `python3 -m venv venv`.  Simply activate the virtual environment in the same console
before you launch the repl and things should work fine assuming the base python
executable versions line up.  Again, see readme if they do not in order to override the
version loaded.
