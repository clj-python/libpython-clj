(ns python.builtins
"No documentation provided"
(:require [libpython-clj2.python :as py]
          [libpython-clj2.python.jvm-handle :refer [py-global-delay]]
          [libpython-clj2.python.bridge-as-jvm :as as-jvm])
(:refer-clojure :exclude [+ - * / float double int long mod byte test char short take partition require max min identity empty mod repeat str load cast type sort conj map range list next hash eval bytes filter compile print set format]))

(defonce ^:private src-obj* (py-global-delay (py/path->py-obj "builtins")))

(def float (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "float"))))
(alter-meta! #'float assoc :doc "Convert a string or number to a floating point number, if possible." :arglists '[[self & [args {:as kwargs}]]])

(def ConnectionResetError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ConnectionResetError"))))
(alter-meta! #'ConnectionResetError assoc :doc "Connection reset." :arglists '[[self & [args {:as kwargs}]]])

(def map (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "map"))))
(alter-meta! #'map assoc :doc "map(func, *iterables) --> map object

Make an iterator that computes the function using arguments from
each of the iterables.  Stops when the shortest iterable is exhausted." :arglists '[[self & [args {:as kwargs}]]])

(def RecursionError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "RecursionError"))))
(alter-meta! #'RecursionError assoc :doc "Recursion limit exceeded." :arglists '[[self & [args {:as kwargs}]]])

(def int (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "int"))))
(alter-meta! #'int assoc :doc "int([x]) -> integer
int(x, base=10) -> integer

Convert a number or string to an integer, or return 0 if no arguments
are given.  If x is a number, return x.__int__().  For floating point
numbers, this truncates towards zero.

If x is not a number or if base is given, then x must be a string,
bytes, or bytearray instance representing an integer literal in the
given base.  The literal can be preceded by '+' or '-' and be surrounded
by whitespace.  The base defaults to 10.  Valid bases are 0 and 2-36.
Base 0 means to interpret the base from the string as an integer literal.
>>> int('0b100', base=0)
4" :arglists '[[self & [args {:as kwargs}]]])

(def object (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "object"))))
(alter-meta! #'object assoc :doc "The base class of the class hierarchy.

When called, it accepts no arguments and returns a new featureless
instance that has no instance attributes and cannot be given any.
" :arglists '[[self & [args {:as kwargs}]]])

(def ^{:doc ""} NotImplemented (as-jvm/generic-pyobject (py-global-delay (py/get-attr @src-obj* "NotImplemented"))))

(def range (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "range"))))
(alter-meta! #'range assoc :doc "range(stop) -> range object
range(start, stop[, step]) -> range object

Return an object that produces a sequence of integers from start (inclusive)
to stop (exclusive) by step.  range(i, j) produces i, i+1, i+2, ..., j-1.
start defaults to 0, and stop is omitted!  range(4) produces 0, 1, 2, 3.
These are exactly the valid indices for a list of 4 elements.
When step is given, it specifies the increment (or decrement)." :arglists '[[self & [args {:as kwargs}]]])

(def TypeError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "TypeError"))))
(alter-meta! #'TypeError assoc :doc "Inappropriate argument type." :arglists '[[self & [args {:as kwargs}]]])

(def PermissionError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "PermissionError"))))
(alter-meta! #'PermissionError assoc :doc "Not enough permissions." :arglists '[[self & [args {:as kwargs}]]])

(def MemoryError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "MemoryError"))))
(alter-meta! #'MemoryError assoc :doc "Out of memory." :arglists '[[self & [args {:as kwargs}]]])

(def KeyboardInterrupt (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "KeyboardInterrupt"))))
(alter-meta! #'KeyboardInterrupt assoc :doc "Program interrupted by user." :arglists '[[self & [args {:as kwargs}]]])

(def BytesWarning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "BytesWarning"))))
(alter-meta! #'BytesWarning assoc :doc "Base class for warnings about bytes and buffer related problems, mostly
related to conversion from str or comparing to str." :arglists '[[self & [args {:as kwargs}]]])

(def min (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "min"))))
(alter-meta! #'min assoc :doc "min(iterable, *[, default=obj, key=func]) -> value
min(arg1, arg2, *args, *[, key=func]) -> value

With a single iterable argument, return its smallest item. The
default keyword-only argument specifies an object to return if
the provided iterable is empty.
With two or more arguments, return the smallest argument." :arglists '[[self & [args {:as kwargs}]]])

(def FileNotFoundError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "FileNotFoundError"))))
(alter-meta! #'FileNotFoundError assoc :doc "File not found." :arglists '[[self & [args {:as kwargs}]]])

(def reversed (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "reversed"))))
(alter-meta! #'reversed assoc :doc "Return a reverse iterator over the values of the given sequence." :arglists '[[self & [args {:as kwargs}]]])

(def list (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "list"))))
(alter-meta! #'list assoc :doc "Built-in mutable sequence.

If no argument is given, the constructor creates a new empty list.
The argument must be an iterable if specified." :arglists '[[self & [args {:as kwargs}]]])

(def license (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "license"))))
(alter-meta! #'license assoc :doc "interactive prompt objects for printing the license text, a list of
    contributors and the copyright notice." :arglists '[[self name data & [{files :files, dirs :dirs}]] [self name data & [{files :files}]] [self name data]])

(def ascii (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ascii"))))
(alter-meta! #'ascii assoc :doc "Return an ASCII-only representation of an object.

As repr(), return a string containing a printable representation of an
object, but escape the non-ASCII characters in the string returned by
repr() using \\\\x, \\\\u or \\\\U escapes. This generates a string similar
to that returned by repr() in Python 2." :arglists '[[obj]])

(def next (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "next"))))
(alter-meta! #'next assoc :doc "next(iterator[, default])

Return the next item from the iterator. If default is given and the iterator
is exhausted, it is returned instead of raising StopIteration." :arglists '[[self & [args {:as kwargs}]]])

(def SyntaxWarning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "SyntaxWarning"))))
(alter-meta! #'SyntaxWarning assoc :doc "Base class for warnings about dubious syntax." :arglists '[[self & [args {:as kwargs}]]])

(def chr (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "chr"))))
(alter-meta! #'chr assoc :doc "Return a Unicode string of one character with ordinal i; 0 <= i <= 0x10ffff." :arglists '[[i]])

(def ArithmeticError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ArithmeticError"))))
(alter-meta! #'ArithmeticError assoc :doc "Base class for arithmetic errors." :arglists '[[self & [args {:as kwargs}]]])

(def BlockingIOError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "BlockingIOError"))))
(alter-meta! #'BlockingIOError assoc :doc "I/O operation would block." :arglists '[[self & [args {:as kwargs}]]])

(def staticmethod (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "staticmethod"))))
(alter-meta! #'staticmethod assoc :doc "staticmethod(function) -> method

Convert a function to be a static method.

A static method does not receive an implicit first argument.
To declare a static method, use this idiom:

     class C:
         @staticmethod
         def f(arg1, arg2, ...):
             ...

It can be called either on the class (e.g. C.f()) or on an instance
(e.g. C().f()). Both the class and the instance are ignored, and
neither is passed implicitly as the first argument to the method.

Static methods in Python are similar to those found in Java or C++.
For a more advanced concept, see the classmethod builtin." :arglists '[[self & [args {:as kwargs}]]])

(def ImportWarning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ImportWarning"))))
(alter-meta! #'ImportWarning assoc :doc "Base class for warnings about probable mistakes in module imports" :arglists '[[self & [args {:as kwargs}]]])

(def FutureWarning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "FutureWarning"))))
(alter-meta! #'FutureWarning assoc :doc "Base class for warnings about constructs that will change semantically
in the future." :arglists '[[self & [args {:as kwargs}]]])

(def ResourceWarning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ResourceWarning"))))
(alter-meta! #'ResourceWarning assoc :doc "Base class for warnings about resource usage." :arglists '[[self & [args {:as kwargs}]]])

(def classmethod (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "classmethod"))))
(alter-meta! #'classmethod assoc :doc "classmethod(function) -> method

Convert a function to be a class method.

A class method receives the class as implicit first argument,
just like an instance method receives the instance.
To declare a class method, use this idiom:

  class C:
      @classmethod
      def f(cls, arg1, arg2, ...):
          ...

It can be called either on the class (e.g. C.f()) or on an instance
(e.g. C().f()).  The instance is ignored except for its class.
If a class method is called for a derived class, the derived class
object is passed as the implied first argument.

Class methods are different than C++ or Java static methods.
If you want those, see the staticmethod builtin." :arglists '[[self & [args {:as kwargs}]]])

(def IndexError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "IndexError"))))
(alter-meta! #'IndexError assoc :doc "Sequence index out of range." :arglists '[[self & [args {:as kwargs}]]])

(def hex (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "hex"))))
(alter-meta! #'hex assoc :doc "Return the hexadecimal representation of an integer.

   >>> hex(12648430)
   '0xc0ffee'" :arglists '[[number]])

(def sum (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "sum"))))
(alter-meta! #'sum assoc :doc "Return the sum of a 'start' value (default: 0) plus an iterable of numbers

When the iterable is empty, return the start value.
This function is intended specifically for use with numeric values and may
reject non-numeric types." :arglists '[[iterable & [{start :start}]] [iterable]])

(def str (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "str"))))
(alter-meta! #'str assoc :doc "str(object='') -> str
str(bytes_or_buffer[, encoding[, errors]]) -> str

Create a new string object from the given object. If encoding or
errors is specified, then the object must expose a data buffer
that will be decoded using the given encoding and error handler.
Otherwise, returns the result of object.__str__() (if defined)
or repr(object).
encoding defaults to sys.getdefaultencoding().
errors defaults to 'strict'." :arglists '[[self & [args {:as kwargs}]]])

(def hash (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "hash"))))
(alter-meta! #'hash assoc :doc "Return the hash value for the given object.

Two objects that compare equal must also have the same hash value, but the
reverse is not necessarily true." :arglists '[[obj]])

(def breakpoint (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "breakpoint"))))
(alter-meta! #'breakpoint assoc :doc "breakpoint(*args, **kws)

Call sys.breakpointhook(*args, **kws).  sys.breakpointhook() must accept
whatever arguments are passed.

By default, this drops you into the pdb debugger." :arglists '[[self & [args {:as kwargs}]]])

(def sorted (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "sorted"))))
(alter-meta! #'sorted assoc :doc "Return a new list containing all items from the iterable in ascending order.

A custom key function can be supplied to customize the sort order, and the
reverse flag can be set to request the result in descending order." :arglists '[[iterable & [{key :key, reverse :reverse}]]])

(def repr (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "repr"))))
(alter-meta! #'repr assoc :doc "Return the canonical string representation of the object.

For many object types, including most builtins, eval(repr(obj)) == obj." :arglists '[[obj]])

(def __loader__ (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "__loader__"))))
(alter-meta! #'__loader__ assoc :doc "Meta path import for built-in modules.

    All methods are either class or static methods to avoid the need to
    instantiate the class.

    " :arglists '[[self & [args {:as kwargs}]]])

(def NotADirectoryError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "NotADirectoryError"))))
(alter-meta! #'NotADirectoryError assoc :doc "Operation only works on directories." :arglists '[[self & [args {:as kwargs}]]])

(def max (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "max"))))
(alter-meta! #'max assoc :doc "max(iterable, *[, default=obj, key=func]) -> value
max(arg1, arg2, *args, *[, key=func]) -> value

With a single iterable argument, return its biggest item. The
default keyword-only argument specifies an object to return if
the provided iterable is empty.
With two or more arguments, return the largest argument." :arglists '[[self & [args {:as kwargs}]]])

(def SystemError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "SystemError"))))
(alter-meta! #'SystemError assoc :doc "Internal error in the Python interpreter.

Please report this to the Python maintainer, along with the traceback,
the Python version, and the hardware/OS platform and version." :arglists '[[self & [args {:as kwargs}]]])

(def isinstance (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "isinstance"))))
(alter-meta! #'isinstance assoc :doc "Return whether an object is an instance of a class or of a subclass thereof.

A tuple, as in ``isinstance(x, (A, B, ...))``, may be given as the target to
check against. This is equivalent to ``isinstance(x, A) or isinstance(x, B)
or ...`` etc." :arglists '[[obj class_or_tuple]])

(def id (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "id"))))
(alter-meta! #'id assoc :doc "Return the identity of an object.

This is guaranteed to be unique among simultaneously existing objects.
(CPython uses the object's memory address.)" :arglists '[[obj]])

(def pow (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "pow"))))
(alter-meta! #'pow assoc :doc "Equivalent to base**exp with 2 arguments or base**exp % mod with 3 arguments

Some types, such as ints, are able to use a more efficient algorithm when
invoked using the three argument form." :arglists '[[base exp & [{mod :mod}]] [base exp]])

(def TimeoutError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "TimeoutError"))))
(alter-meta! #'TimeoutError assoc :doc "Timeout expired." :arglists '[[self & [args {:as kwargs}]]])

(def delattr (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "delattr"))))
(alter-meta! #'delattr assoc :doc "Deletes the named attribute from the given object.

delattr(x, 'y') is equivalent to ``del x.y''" :arglists '[[obj name]])

(def FloatingPointError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "FloatingPointError"))))
(alter-meta! #'FloatingPointError assoc :doc "Floating point operation failed." :arglists '[[self & [args {:as kwargs}]]])

(def GeneratorExit (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "GeneratorExit"))))
(alter-meta! #'GeneratorExit assoc :doc "Request that a generator exit." :arglists '[[self & [args {:as kwargs}]]])

(def ConnectionAbortedError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ConnectionAbortedError"))))
(alter-meta! #'ConnectionAbortedError assoc :doc "Connection aborted." :arglists '[[self & [args {:as kwargs}]]])

(def UnicodeTranslateError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "UnicodeTranslateError"))))
(alter-meta! #'UnicodeTranslateError assoc :doc "Unicode translation error." :arglists '[[self & [args {:as kwargs}]]])

(def UnicodeDecodeError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "UnicodeDecodeError"))))
(alter-meta! #'UnicodeDecodeError assoc :doc "Unicode decoding error." :arglists '[[self & [args {:as kwargs}]]])

(def exec (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "exec"))))
(alter-meta! #'exec assoc :doc "Execute the given source in the context of globals and locals.

The source may be a string representing one or more Python statements
or a code object as returned by compile().
The globals must be a dictionary and locals can be any mapping,
defaulting to the current globals and locals.
If only globals is given, locals defaults to it." :arglists '[[source & [{globals :globals, locals :locals}]] [source & [{globals :globals}]] [source]])

(def divmod (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "divmod"))))
(alter-meta! #'divmod assoc :doc "Return the tuple (x//y, x%y).  Invariant: div*y + mod == x." :arglists '[[x y]])

(def any (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "any"))))
(alter-meta! #'any assoc :doc "Return True if bool(x) is True for any x in the iterable.

If the iterable is empty, return False." :arglists '[[iterable]])

(def super (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "super"))))
(alter-meta! #'super assoc :doc "super() -> same as super(__class__, <first argument>)
super(type) -> unbound super object
super(type, obj) -> bound super object; requires isinstance(obj, type)
super(type, type2) -> bound super object; requires issubclass(type2, type)
Typical use to call a cooperative superclass method:
class C(B):
    def meth(self, arg):
        super().meth(arg)
This works for class methods too:
class C(B):
    @classmethod
    def cmeth(cls, arg):
        super().cmeth(arg)
" :arglists '[[self & [args {:as kwargs}]]])

(def memoryview (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "memoryview"))))
(alter-meta! #'memoryview assoc :doc "Create a new memoryview object which references the given object." :arglists '[[self & [args {:as kwargs}]]])

(def enumerate (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "enumerate"))))
(alter-meta! #'enumerate assoc :doc "Return an enumerate object.

  iterable
    an object supporting iteration

The enumerate object yields pairs containing a count (from start, which
defaults to zero) and a value yielded by the iterable argument.

enumerate is useful for obtaining an indexed list:
    (0, seq[0]), (1, seq[1]), (2, seq[2]), ..." :arglists '[[self & [args {:as kwargs}]]])

(def dict (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "dict"))))
(alter-meta! #'dict assoc :doc "dict() -> new empty dictionary
dict(mapping) -> new dictionary initialized from a mapping object's
    (key, value) pairs
dict(iterable) -> new dictionary initialized as if via:
    d = {}
    for k, v in iterable:
        d[k] = v
dict(**kwargs) -> new dictionary initialized with the name=value pairs
    in the keyword argument list.  For example:  dict(one=1, two=2)" :arglists '[[self & [args {:as kwargs}]]])

(def OSError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "OSError"))))
(alter-meta! #'OSError assoc :doc "Base class for I/O related errors." :arglists '[[self & [args {:as kwargs}]]])

(def ReferenceError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ReferenceError"))))
(alter-meta! #'ReferenceError assoc :doc "Weak ref proxy used after referent went away." :arglists '[[self & [args {:as kwargs}]]])

(def UnicodeError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "UnicodeError"))))
(alter-meta! #'UnicodeError assoc :doc "Unicode related error." :arglists '[[self & [args {:as kwargs}]]])

(def FileExistsError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "FileExistsError"))))
(alter-meta! #'FileExistsError assoc :doc "File already exists." :arglists '[[self & [args {:as kwargs}]]])

(def InterruptedError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "InterruptedError"))))
(alter-meta! #'InterruptedError assoc :doc "Interrupted by signal." :arglists '[[self & [args {:as kwargs}]]])

(def Warning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "Warning"))))
(alter-meta! #'Warning assoc :doc "Base class for warning categories." :arglists '[[self & [args {:as kwargs}]]])

(def eval (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "eval"))))
(alter-meta! #'eval assoc :doc "Evaluate the given source in the context of globals and locals.

The source may be a string representing a Python expression
or a code object as returned by compile().
The globals must be a dictionary and locals can be any mapping,
defaulting to the current globals and locals.
If only globals is given, locals defaults to it." :arglists '[[source & [{globals :globals, locals :locals}]] [source & [{globals :globals}]] [source]])

(def bool (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "bool"))))
(alter-meta! #'bool assoc :doc "bool(x) -> bool

Returns True when the argument x is true, False otherwise.
The builtins True and False are the only two instances of the class bool.
The class bool is a subclass of the class int, and cannot be subclassed." :arglists '[[self & [args {:as kwargs}]]])

(def SyntaxError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "SyntaxError"))))
(alter-meta! #'SyntaxError assoc :doc "Invalid syntax." :arglists '[[self & [args {:as kwargs}]]])

(def UnicodeEncodeError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "UnicodeEncodeError"))))
(alter-meta! #'UnicodeEncodeError assoc :doc "Unicode encoding error." :arglists '[[self & [args {:as kwargs}]]])

(def ord (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ord"))))
(alter-meta! #'ord assoc :doc "Return the Unicode code point for a one-character string." :arglists '[[c]])

(def callable (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "callable"))))
(alter-meta! #'callable assoc :doc "Return whether the object is callable (i.e., some kind of function).

Note that classes are callable, as are instances of classes with a
__call__() method." :arglists '[[obj]])

(def EnvironmentError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "EnvironmentError"))))
(alter-meta! #'EnvironmentError assoc :doc "Base class for I/O related errors." :arglists '[[self & [args {:as kwargs}]]])

(def RuntimeWarning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "RuntimeWarning"))))
(alter-meta! #'RuntimeWarning assoc :doc "Base class for warnings about dubious runtime behavior." :arglists '[[self & [args {:as kwargs}]]])

(def quit (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "quit"))))
(alter-meta! #'quit assoc :doc "" :arglists '[[self name eof]])

(def NameError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "NameError"))))
(alter-meta! #'NameError assoc :doc "Name not found globally." :arglists '[[self & [args {:as kwargs}]]])

(def DeprecationWarning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "DeprecationWarning"))))
(alter-meta! #'DeprecationWarning assoc :doc "Base class for warnings about deprecated features." :arglists '[[self & [args {:as kwargs}]]])

(def ProcessLookupError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ProcessLookupError"))))
(alter-meta! #'ProcessLookupError assoc :doc "Process not found." :arglists '[[self & [args {:as kwargs}]]])

(def bin (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "bin"))))
(alter-meta! #'bin assoc :doc "Return the binary representation of an integer.

   >>> bin(2796202)
   '0b1010101010101010101010'" :arglists '[[number]])

(def ^{:doc "The specification for a module, used for loading.

    A module's spec is the source for information about the module.  For
    data associated with the module, including source, use the spec's
    loader.

    `name` is the absolute name of the module.  `loader` is the loader
    to use when loading the module.  `parent` is the name of the
    package the module is in.  The parent is derived from the name.

    `is_package` determines if the module is considered a package or
    not.  On modules this is reflected by the `__path__` attribute.

    `origin` is the specific location used by the loader from which to
    load the module, if that information is available.  When filename is
    set, origin will match.

    `has_location` indicates that a spec's \"origin\" reflects a location.
    When this is True, `__file__` attribute of the module is set.

    `cached` is the location of the cached bytecode file, if any.  It
    corresponds to the `__cached__` attribute.

    `submodule_search_locations` is the sequence of path entries to
    search when importing submodules.  If set, is_package should be
    True--and False otherwise.

    Packages are simply modules that (may) have submodules.  If a spec
    has a non-None value in `submodule_search_locations`, the import
    system will consider modules loaded from the spec as packages.

    Only finders (see importlib.abc.MetaPathFinder and
    importlib.abc.PathEntryFinder) should modify ModuleSpec instances.

    "} __spec__ (as-jvm/generic-pyobject (py-global-delay (py/get-attr @src-obj* "__spec__"))))

(def bytes (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "bytes"))))
(alter-meta! #'bytes assoc :doc "bytes(iterable_of_ints) -> bytes
bytes(string, encoding[, errors]) -> bytes
bytes(bytes_or_buffer) -> immutable copy of bytes_or_buffer
bytes(int) -> bytes object of size given by the parameter initialized with null bytes
bytes() -> empty bytes object

Construct an immutable array of bytes from:
  - an iterable yielding integers in range(256)
  - a text string encoded using the specified encoding
  - any object implementing the buffer API.
  - an integer" :arglists '[[self & [args {:as kwargs}]]])

(def dir (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "dir"))))
(alter-meta! #'dir assoc :doc "dir([object]) -> list of strings

If called without an argument, return the names in the current scope.
Else, return an alphabetized list of names comprising (some of) the attributes
of the given object, and of attributes reachable from it.
If the object supplies a method named __dir__, it will be used; otherwise
the default dir() logic is used and returns:
  for a module object: the module's attributes.
  for a class object:  its attributes, and recursively the attributes
    of its bases.
  for any other object: its attributes, its class's attributes, and
    recursively the attributes of its class's base classes." :arglists '[[self & [args {:as kwargs}]]])

(def filter (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "filter"))))
(alter-meta! #'filter assoc :doc "filter(function or None, iterable) --> filter object

Return an iterator yielding those items of iterable for which function(item)
is true. If function is None, return the items that are true." :arglists '[[self & [args {:as kwargs}]]])

(def property (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "property"))))
(alter-meta! #'property assoc :doc "Property attribute.

  fget
    function to be used for getting an attribute value
  fset
    function to be used for setting an attribute value
  fdel
    function to be used for del'ing an attribute
  doc
    docstring

Typical use is to define a managed attribute x:

class C(object):
    def getx(self): return self._x
    def setx(self, value): self._x = value
    def delx(self): del self._x
    x = property(getx, setx, delx, \"I'm the 'x' property.\")

Decorators make defining new properties or modifying existing ones easy:

class C(object):
    @property
    def x(self):
        \"I am the 'x' property.\"
        return self._x
    @x.setter
    def x(self, value):
        self._x = value
    @x.deleter
    def x(self):
        del self._x" :arglists '[[self & [args {:as kwargs}]]])

(def KeyError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "KeyError"))))
(alter-meta! #'KeyError assoc :doc "Mapping key not found." :arglists '[[self & [args {:as kwargs}]]])

(def ^{:doc ""} __debug__ (as-jvm/generic-pyobject (py-global-delay (py/get-attr @src-obj* "__debug__"))))

(def IOError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "IOError"))))
(alter-meta! #'IOError assoc :doc "Base class for I/O related errors." :arglists '[[self & [args {:as kwargs}]]])

(def BufferError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "BufferError"))))
(alter-meta! #'BufferError assoc :doc "Buffer error." :arglists '[[self & [args {:as kwargs}]]])

(def bytearray (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "bytearray"))))
(alter-meta! #'bytearray assoc :doc "bytearray(iterable_of_ints) -> bytearray
bytearray(string, encoding[, errors]) -> bytearray
bytearray(bytes_or_buffer) -> mutable copy of bytes_or_buffer
bytearray(int) -> bytes array of size given by the parameter initialized with null bytes
bytearray() -> empty bytes array

Construct a mutable bytearray object from:
  - an iterable yielding integers in range(256)
  - a text string encoded using the specified encoding
  - a bytes or a buffer object
  - any object implementing the buffer API.
  - an integer" :arglists '[[self & [args {:as kwargs}]]])

(def compile (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "compile"))))
(alter-meta! #'compile assoc :doc "Compile source into a code object that can be executed by exec() or eval().

The source code may represent a Python module, statement or expression.
The filename will be used for run-time error messages.
The mode must be 'exec' to compile a module, 'single' to compile a
single (interactive) statement, or 'eval' to compile an expression.
The flags argument, if present, controls which future statements influence
the compilation of the code.
The dont_inherit argument, if true, stops the compilation inheriting
the effects of any future statements in effect in the code calling
compile; if absent or false these statements do influence the compilation,
in addition to any features explicitly specified." :arglists '[[source filename mode & [{flags :flags, dont_inherit :dont_inherit, optimize :optimize, _feature_version :_feature_version}]] [source filename mode & [{flags :flags, dont_inherit :dont_inherit, _feature_version :_feature_version}]] [source filename mode & [{flags :flags, _feature_version :_feature_version}]] [source filename mode & [{_feature_version :_feature_version}]]])

(def input (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "input"))))
(alter-meta! #'input assoc :doc "Read a string from standard input.  The trailing newline is stripped.

The prompt string, if given, is printed to standard output without a
trailing newline before reading input.

If the user hits EOF (*nix: Ctrl-D, Windows: Ctrl-Z+Return), raise EOFError.
On *nix systems, readline is used if available." :arglists '[[& [{prompt :prompt}]] []])

(def BaseException (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "BaseException"))))
(alter-meta! #'BaseException assoc :doc "Common base class for all exceptions" :arglists '[[self & [args {:as kwargs}]]])

(def ImportError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ImportError"))))
(alter-meta! #'ImportError assoc :doc "Import can't find module, or can't find name in module." :arglists '[[self & [args {:as kwargs}]]])

(def setattr (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "setattr"))))
(alter-meta! #'setattr assoc :doc "Sets the named attribute on the given object to the specified value.

setattr(x, 'y', v) is equivalent to ``x.y = v''" :arglists '[[obj name value]])

(def __build_class__ (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "__build_class__"))))
(alter-meta! #'__build_class__ assoc :doc "__build_class__(func, name, /, *bases, [metaclass], **kwds) -> class

Internal helper function used by the class statement." :arglists '[[self & [args {:as kwargs}]]])

(def copyright (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "copyright"))))
(alter-meta! #'copyright assoc :doc "interactive prompt objects for printing the license text, a list of
    contributors and the copyright notice." :arglists '[[self name data & [{files :files, dirs :dirs}]] [self name data & [{files :files}]] [self name data]])

(def type (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "type"))))
(alter-meta! #'type assoc :doc "type(object_or_name, bases, dict)
type(object) -> the object's type
type(name, bases, dict) -> a new type" :arglists '[[self & [args {:as kwargs}]]])

(def LookupError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "LookupError"))))
(alter-meta! #'LookupError assoc :doc "Base class for lookup errors." :arglists '[[self & [args {:as kwargs}]]])

(def ZeroDivisionError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ZeroDivisionError"))))
(alter-meta! #'ZeroDivisionError assoc :doc "Second argument to a division or modulo operation was zero." :arglists '[[self & [args {:as kwargs}]]])

(def globals (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "globals"))))
(alter-meta! #'globals assoc :doc "Return the dictionary containing the current scope's global variables.

NOTE: Updates to this dictionary *will* affect name lookups in the current
global scope and vice-versa." :arglists '[[]])

(def OverflowError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "OverflowError"))))
(alter-meta! #'OverflowError assoc :doc "Result too large to be represented." :arglists '[[self & [args {:as kwargs}]]])

(def abs (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "abs"))))
(alter-meta! #'abs assoc :doc "Return the absolute value of the argument." :arglists '[[x]])

(def ConnectionRefusedError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ConnectionRefusedError"))))
(alter-meta! #'ConnectionRefusedError assoc :doc "Connection refused." :arglists '[[self & [args {:as kwargs}]]])

(def help (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "help"))))
(alter-meta! #'help assoc :doc "Define the builtin 'help'.

    This is a wrapper around pydoc.help that provides a helpful message
    when 'help' is typed at the Python interactive prompt.

    Calling help() at the Python prompt starts an interactive help session.
    Calling help(thing) prints help for the python object 'thing'.
    " :arglists '[[self & [args {:as kwargs}]]])

(def UserWarning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "UserWarning"))))
(alter-meta! #'UserWarning assoc :doc "Base class for warnings generated by user code." :arglists '[[self & [args {:as kwargs}]]])

(def slice (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "slice"))))
(alter-meta! #'slice assoc :doc "slice(stop)
slice(start, stop[, step])

Create a slice object.  This is used for extended slicing (e.g. a[0:10:2])." :arglists '[[self & [args {:as kwargs}]]])

(def TabError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "TabError"))))
(alter-meta! #'TabError assoc :doc "Improper mixture of spaces and tabs." :arglists '[[self & [args {:as kwargs}]]])

(def print (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "print"))))
(alter-meta! #'print assoc :doc "print(value, ..., sep=' ', end='\\n', file=sys.stdout, flush=False)

Prints the values to a stream, or to sys.stdout by default.
Optional keyword arguments:
file:  a file-like object (stream); defaults to the current sys.stdout.
sep:   string inserted between values, default a space.
end:   string appended after the last value, default a newline.
flush: whether to forcibly flush the stream." :arglists '[[self & [args {:as kwargs}]]])

(def exit (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "exit"))))
(alter-meta! #'exit assoc :doc "" :arglists '[[self name eof]])

(def EOFError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "EOFError"))))
(alter-meta! #'EOFError assoc :doc "Read beyond end of file." :arglists '[[self & [args {:as kwargs}]]])

(def PyAssertionError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "AssertionError"))))
(alter-meta! #'PyAssertionError assoc :doc "Assertion failed." :arglists '[[self & [args {:as kwargs}]]])

(def hasattr (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "hasattr"))))
(alter-meta! #'hasattr assoc :doc "Return whether the object has an attribute with the given name.

This is done by calling getattr(obj, name) and catching AttributeError." :arglists '[[obj name]])

(def len (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "len"))))
(alter-meta! #'len assoc :doc "Return the number of items in a container." :arglists '[[obj]])

(def set (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "set"))))
(alter-meta! #'set assoc :doc "set() -> new empty set object
set(iterable) -> new set object

Build an unordered collection of unique elements." :arglists '[[self & [args {:as kwargs}]]])

(def BrokenPipeError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "BrokenPipeError"))))
(alter-meta! #'BrokenPipeError assoc :doc "Broken pipe." :arglists '[[self & [args {:as kwargs}]]])

(def all (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "all"))))
(alter-meta! #'all assoc :doc "Return True if bool(x) is True for all values x in the iterable.

If the iterable is empty, return True." :arglists '[[iterable]])

(def PendingDeprecationWarning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "PendingDeprecationWarning"))))
(alter-meta! #'PendingDeprecationWarning assoc :doc "Base class for warnings about features which will be deprecated
in the future." :arglists '[[self & [args {:as kwargs}]]])

(def tuple (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "tuple"))))
(alter-meta! #'tuple assoc :doc "Built-in immutable sequence.

If no argument is given, the constructor returns an empty tuple.
If iterable is specified the tuple is initialized from iterable's items.

If the argument is a tuple, the return value is the same object." :arglists '[[self & [args {:as kwargs}]]])

(def ChildProcessError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ChildProcessError"))))
(alter-meta! #'ChildProcessError assoc :doc "Child process error." :arglists '[[self & [args {:as kwargs}]]])

(def UnboundLocalError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "UnboundLocalError"))))
(alter-meta! #'UnboundLocalError assoc :doc "Local name referenced but not bound to a value." :arglists '[[self & [args {:as kwargs}]]])

(def ModuleNotFoundError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ModuleNotFoundError"))))
(alter-meta! #'ModuleNotFoundError assoc :doc "Module not found." :arglists '[[self & [args {:as kwargs}]]])

(def ValueError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ValueError"))))
(alter-meta! #'ValueError assoc :doc "Inappropriate argument value (of correct type)." :arglists '[[self & [args {:as kwargs}]]])

(def iter (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "iter"))))
(alter-meta! #'iter assoc :doc "iter(iterable) -> iterator
iter(callable, sentinel) -> iterator

Get an iterator from an object.  In the first form, the argument must
supply its own iterator, or be a sequence.
In the second form, the callable is called until it returns the sentinel." :arglists '[[self & [args {:as kwargs}]]])

(def ^{:doc ""} Ellipsis (as-jvm/generic-pyobject (py-global-delay (py/get-attr @src-obj* "Ellipsis"))))

(def open (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "open"))))
(alter-meta! #'open assoc :doc "Open file and return a stream.  Raise OSError upon failure.

file is either a text or byte string giving the name (and the path
if the file isn't in the current working directory) of the file to
be opened or an integer file descriptor of the file to be
wrapped. (If a file descriptor is given, it is closed when the
returned I/O object is closed, unless closefd is set to False.)

mode is an optional string that specifies the mode in which the file
is opened. It defaults to 'r' which means open for reading in text
mode.  Other common values are 'w' for writing (truncating the file if
it already exists), 'x' for creating and writing to a new file, and
'a' for appending (which on some Unix systems, means that all writes
append to the end of the file regardless of the current seek position).
In text mode, if encoding is not specified the encoding used is platform
dependent: locale.getpreferredencoding(False) is called to get the
current locale encoding. (For reading and writing raw bytes use binary
mode and leave encoding unspecified.) The available modes are:

========= ===============================================================
Character Meaning
--------- ---------------------------------------------------------------
'r'       open for reading (default)
'w'       open for writing, truncating the file first
'x'       create a new file and open it for writing
'a'       open for writing, appending to the end of the file if it exists
'b'       binary mode
't'       text mode (default)
'+'       open a disk file for updating (reading and writing)
'U'       universal newline mode (deprecated)
========= ===============================================================

The default mode is 'rt' (open for reading text). For binary random
access, the mode 'w+b' opens and truncates the file to 0 bytes, while
'r+b' opens the file without truncation. The 'x' mode implies 'w' and
raises an `FileExistsError` if the file already exists.

Python distinguishes between files opened in binary and text modes,
even when the underlying operating system doesn't. Files opened in
binary mode (appending 'b' to the mode argument) return contents as
bytes objects without any decoding. In text mode (the default, or when
't' is appended to the mode argument), the contents of the file are
returned as strings, the bytes having been first decoded using a
platform-dependent encoding or using the specified encoding if given.

'U' mode is deprecated and will raise an exception in future versions
of Python.  It has no effect in Python 3.  Use newline to control
universal newlines mode.

buffering is an optional integer used to set the buffering policy.
Pass 0 to switch buffering off (only allowed in binary mode), 1 to select
line buffering (only usable in text mode), and an integer > 1 to indicate
the size of a fixed-size chunk buffer.  When no buffering argument is
given, the default buffering policy works as follows:

* Binary files are buffered in fixed-size chunks; the size of the buffer
  is chosen using a heuristic trying to determine the underlying device's
  \"block size\" and falling back on `io.DEFAULT_BUFFER_SIZE`.
  On many systems, the buffer will typically be 4096 or 8192 bytes long.

* \"Interactive\" text files (files for which isatty() returns True)
  use line buffering.  Other text files use the policy described above
  for binary files.

encoding is the name of the encoding used to decode or encode the
file. This should only be used in text mode. The default encoding is
platform dependent, but any encoding supported by Python can be
passed.  See the codecs module for the list of supported encodings.

errors is an optional string that specifies how encoding errors are to
be handled---this argument should not be used in binary mode. Pass
'strict' to raise a ValueError exception if there is an encoding error
(the default of None has the same effect), or pass 'ignore' to ignore
errors. (Note that ignoring encoding errors can lead to data loss.)
See the documentation for codecs.register or run 'help(codecs.Codec)'
for a list of the permitted encoding error strings.

newline controls how universal newlines works (it only applies to text
mode). It can be None, '', '\\n', '\\r', and '\\r\\n'.  It works as
follows:

* On input, if newline is None, universal newlines mode is
  enabled. Lines in the input can end in '\\n', '\\r', or '\\r\\n', and
  these are translated into '\\n' before being returned to the
  caller. If it is '', universal newline mode is enabled, but line
  endings are returned to the caller untranslated. If it has any of
  the other legal values, input lines are only terminated by the given
  string, and the line ending is returned to the caller untranslated.

* On output, if newline is None, any '\\n' characters written are
  translated to the system default line separator, os.linesep. If
  newline is '' or '\\n', no translation takes place. If newline is any
  of the other legal values, any '\\n' characters written are translated
  to the given string.

If closefd is False, the underlying file descriptor will be kept open
when the file is closed. This does not work when a file name is given
and must be True in that case.

A custom opener can be used by passing a callable as *opener*. The
underlying file descriptor for the file object is then obtained by
calling *opener* with (*file*, *flags*). *opener* must return an open
file descriptor (passing os.open as *opener* results in functionality
similar to passing None).

open() returns a file object whose type depends on the mode, and
through which the standard file operations such as reading and writing
are performed. When open() is used to open a file in a text mode ('w',
'r', 'wt', 'rt', etc.), it returns a TextIOWrapper. When used to open
a file in a binary mode, the returned class varies: in read binary
mode, it returns a BufferedReader; in write binary and append binary
modes, it returns a BufferedWriter, and in read/write mode, it returns
a BufferedRandom.

It is also possible to use a string or bytearray as a file for both
reading and writing. For strings StringIO can be used like a file
opened in a text mode, and for bytes a BytesIO can be used like a file
opened in a binary mode." :arglists '[[file & [{mode :mode, buffering :buffering, encoding :encoding, errors :errors, newline :newline, closefd :closefd, opener :opener}]] [file & [{mode :mode, buffering :buffering, encoding :encoding, errors :errors, newline :newline, closefd :closefd}]] [file & [{mode :mode, buffering :buffering, encoding :encoding, errors :errors, newline :newline}]] [file & [{mode :mode, buffering :buffering, encoding :encoding, errors :errors}]] [file & [{mode :mode, buffering :buffering, encoding :encoding}]] [file & [{mode :mode, buffering :buffering}]] [file & [{mode :mode}]] [file]])

(def ^{:doc ""} __doc__ (as-jvm/generic-pyobject (py-global-delay (py/get-attr @src-obj* "__doc__"))))

(def __import__ (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "__import__"))))
(alter-meta! #'__import__ assoc :doc "__import__(name, globals=None, locals=None, fromlist=(), level=0) -> module

Import a module. Because this function is meant for use by the Python
interpreter and not for general use, it is better to use
importlib.import_module() to programmatically import a module.

The globals argument is only used to determine the context;
they are not modified.  The locals argument is unused.  The fromlist
should be a list of names to emulate ``from name import ...'', or an
empty list to emulate ``import name''.
When importing a module from a package, note that __import__('A.B', ...)
returns package A when fromlist is empty, but its submodule B when
fromlist is not empty.  The level argument is used to determine whether to
perform absolute or relative imports: 0 is absolute, while a positive number
is the number of parent directories to search relative to the current module." :arglists '[[self & [args {:as kwargs}]]])

(def SystemExit (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "SystemExit"))))
(alter-meta! #'SystemExit assoc :doc "Request to exit from the interpreter." :arglists '[[self & [args {:as kwargs}]]])

(def ConnectionError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "ConnectionError"))))
(alter-meta! #'ConnectionError assoc :doc "Connection error." :arglists '[[self & [args {:as kwargs}]]])

(def round (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "round"))))
(alter-meta! #'round assoc :doc "Round a number to a given precision in decimal digits.

The return value is an integer if ndigits is omitted or None.  Otherwise
the return value has the same type as the number.  ndigits may be negative." :arglists '[[number & [{ndigits :ndigits}]] [number]])

(def complex (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "complex"))))
(alter-meta! #'complex assoc :doc "Create a complex number from a real part and an optional imaginary part.

This is equivalent to (real + imag*1j) where imag defaults to 0." :arglists '[[self & [args {:as kwargs}]]])

(def UnicodeWarning (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "UnicodeWarning"))))
(alter-meta! #'UnicodeWarning assoc :doc "Base class for warnings about Unicode related problems, mostly
related to conversion problems." :arglists '[[self & [args {:as kwargs}]]])

(def ^{:doc ""} __name__ (as-jvm/generic-pyobject (py-global-delay (py/get-attr @src-obj* "__name__"))))

(def oct (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "oct"))))
(alter-meta! #'oct assoc :doc "Return the octal representation of an integer.

   >>> oct(342391)
   '0o1234567'" :arglists '[[number]])

(def locals (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "locals"))))
(alter-meta! #'locals assoc :doc "Return a dictionary containing the current scope's local variables.

NOTE: Whether or not updates to this dictionary will affect name lookups in
the local scope and vice-versa is *implementation dependent* and not
covered by any backwards compatibility guarantees." :arglists '[[]])

(def NotImplementedError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "NotImplementedError"))))
(alter-meta! #'NotImplementedError assoc :doc "Method or function hasn't been implemented yet." :arglists '[[self & [args {:as kwargs}]]])

(def getattr (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "getattr"))))
(alter-meta! #'getattr assoc :doc "getattr(object, name[, default]) -> value

Get a named attribute from an object; getattr(x, 'y') is equivalent to x.y.
When a default argument is given, it is returned when the attribute doesn't
exist; without it, an exception is raised in that case." :arglists '[[self & [args {:as kwargs}]]])

(def ^{:doc ""} True (as-jvm/generic-pyobject (py-global-delay (py/get-attr @src-obj* "True"))))

(def zip (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "zip"))))
(alter-meta! #'zip assoc :doc "zip(*iterables) --> A zip object yielding tuples until an input is exhausted.

   >>> list(zip('abcdefg', range(3), range(4)))
   [('a', 0, 0), ('b', 1, 1), ('c', 2, 2)]

The zip object yields n-length tuples, where n is the number of iterables
passed as positional arguments to zip().  The i-th element in every tuple
comes from the i-th iterable argument to zip().  This continues until the
shortest argument is exhausted." :arglists '[[self & [args {:as kwargs}]]])

(def PyException (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "Exception"))))
(alter-meta! #'PyException assoc :doc "Common base class for all non-exit exceptions." :arglists '[[self & [args {:as kwargs}]]])

(def IndentationError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "IndentationError"))))
(alter-meta! #'IndentationError assoc :doc "Improper indentation." :arglists '[[self & [args {:as kwargs}]]])

(def RuntimeError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "RuntimeError"))))
(alter-meta! #'RuntimeError assoc :doc "Unspecified run-time error." :arglists '[[self & [args {:as kwargs}]]])

(def ^{:doc ""} __package__ (as-jvm/generic-pyobject (py-global-delay (py/get-attr @src-obj* "__package__"))))

(def format (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "format"))))
(alter-meta! #'format assoc :doc "Return value.__format__(format_spec)

format_spec defaults to the empty string.
See the Format Specification Mini-Language section of help('FORMATTING') for
details." :arglists '[[value & [{format_spec :format_spec}]] [value]])

(def credits (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "credits"))))
(alter-meta! #'credits assoc :doc "interactive prompt objects for printing the license text, a list of
    contributors and the copyright notice." :arglists '[[self name data & [{files :files, dirs :dirs}]] [self name data & [{files :files}]] [self name data]])

(def StopIteration (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "StopIteration"))))
(alter-meta! #'StopIteration assoc :doc "Signal the end from iterator.__next__()." :arglists '[[self & [args {:as kwargs}]]])

(def StopAsyncIteration (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "StopAsyncIteration"))))
(alter-meta! #'StopAsyncIteration assoc :doc "Signal the end from iterator.__anext__()." :arglists '[[self & [args {:as kwargs}]]])

(def frozenset (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "frozenset"))))
(alter-meta! #'frozenset assoc :doc "frozenset() -> empty frozenset object
frozenset(iterable) -> frozenset object

Build an immutable unordered collection of unique elements." :arglists '[[self & [args {:as kwargs}]]])

(def AttributeError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "AttributeError"))))
(alter-meta! #'AttributeError assoc :doc "Attribute not found." :arglists '[[self & [args {:as kwargs}]]])

(def vars (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "vars"))))
(alter-meta! #'vars assoc :doc "vars([object]) -> dictionary

Without arguments, equivalent to locals().
With an argument, equivalent to object.__dict__." :arglists '[[self & [args {:as kwargs}]]])

(def IsADirectoryError (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "IsADirectoryError"))))
(alter-meta! #'IsADirectoryError assoc :doc "Operation doesn't work on directories." :arglists '[[self & [args {:as kwargs}]]])

(def issubclass (as-jvm/generic-callable-pyobject (py-global-delay (py/get-attr @src-obj* "issubclass"))))
(alter-meta! #'issubclass assoc :doc "Return whether 'cls' is a derived from another class or is the same class.

A tuple, as in ``issubclass(x, (A, B, ...))``, may be given as the target to
check against. This is equivalent to ``issubclass(x, A) or issubclass(x, B)
or ...`` etc." :arglists '[[cls class_or_tuple]])