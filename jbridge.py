"""Python bindings to start a Clojure repl from a python process.  Relies on
libpython-clj being in a deps.edn pathway as clojure is called from the command
 line to build the classpath.  Expects javabridge to be installed and functional.

Javabridge will dynamically find the java library that corresponds with calling 'java'
 from the command line and load it.  We then initialize Clojure and provide pathways
to require namespaces, find symbols, and call functions.

There are two import initialization methods - init_clojure and init_clojure_repl - these
take care of starting up everything in the correct order.
"""
import subprocess
import javabridge

try:
    from collections.abc import Callable  # noqa
except ImportError:
    from collections import Callable




def init_clojure_runtime():
    javabridge.static_call("clojure/lang/RT", "init", "()V")


def find_clj_fn(fn_ns, fn_name):
    return javabridge.static_call("clojure/lang/RT",
                                  "var",
                                  "(Ljava/lang/String;Ljava/lang/String;)Lclojure/lang/Var;",
                                  fn_ns,
                                  fn_name)


class CLJFn(Callable):
    applyTo = javabridge.make_method("applyTo", "(clojure/lang/ISeq;)Ljava/lang/Object;")
    def __init__(self, fn_ns, fn_name):
        self.o = find_clj_fn(fn_ns, fn_name)

    def __call__(self, *args, **kw_args):
        if not kw_args:
            invoker = getattr(self, "invoke"+str(len(args)))
            return invoker(*args)
        else:
            raise Exception("Unable to handle hwargs for now")
        print(len(args), len(kw_args))


for i in range(20):
    opargs = ""
    for j in range(i):
        opargs += "Ljava/lang/Object;"
    setattr(CLJFn, "invoke" + str(i),
            javabridge.make_method("invoke", "(" + opargs + ")Ljava/lang/Object;" ))


def resolve_fn(namespaced_name):
    ns_name, sym_name = namespaced_name.split("/")
    return CLJFn(ns_name, sym_name)


def resolve_call_fn(namespaced_fn_name, *args):
    return resolve_fn(namespaced_fn_name)(*args)

def symbol(sym_name):
    return javabridge.static_call("clojure/lang/Symbol", "intern",
                                  "(Ljava/lang/String;)Lclojure/lang/Symbol;", sym_name)

__REQUIRE_FN = None

def require(ns_name):
    if not __REQUIRE_FN:
        _REQUIRE_FN = resolve_fn("clojure.core/require")
    return _REQUIRE_FN(symbol(ns_name))


def init_libpy_embedded():
    require("libpython-clj2.python")
    return resolve_call_fn("libpython-clj2.embedded/initialize-embedded!")


def classpath(classpath_args=[]):
    """Call clojure at the command line and return the classpath in as a list of
    strings.  Clojure will pick up a local deps.edn or deps can be specified inline."""
    return subprocess.check_output(['clojure'] + list(classpath_args) + ['-Spath']).decode("utf-8").strip().split(':')

DEFAULT_NREPL_VERSION="0.8.3"
DEFAULT_CIDER_NREPL_VERSION="0.25.5"


def repl_classpath(nrepl_version=DEFAULT_NREPL_VERSION,
                   cider_nrepl_version=DEFAULT_CIDER_NREPL_VERSION,
                   classpath_args=[]):
    """Return the classpath with the correct deps to run nrepl and cider.
    Positional arguments are added after the -Sdeps argument to start the
    nrepl server."""
    return deps_classpath(classpath_args=["-Sdeps", '{:deps {nrepl/nrepl {:mvn/version "%s"} cider/cider-nrepl {:mvn/version "%s"}}}' % (nrepl_version, cider_nrepl_version)]
                        + list(classpath_args))


def init_clojure(classpath_args=[])
"""Initialize a vanilla clojure process using the clojure command line to output
the classpath to use for the java vm. At the return of this function clojure is
initialized and libpython-clj2.python's public functions will work.

* classpath_args - List of arguments that will be passed to the clojure command line
process when building the classpath.
"""
    javabridge.start_vm(run_headless=True,
                        class_path=deps_classpath(classpath_args=classpath_args))
    init_clojure_runtime()
    init_libpy_embedded()
    return True


def init_clojure_repl(**kw_args)
    """Initialize clojure with extra arguments specifically for embedding a cider-nrepl
server.  Then start an nrepl server.  The port will both be printed to stdout and
output to a .nrepl_server file.  This function does not return as it leaves the GIL
released so that repl threads have access to Python.  libpython-clj2.python is
initialized 'require-python' pathways should work.

* classpath_args - List of additional arguments that be passed to the clojure process
  when building the classpath.
"""
    javabridge.start_vm(run_headless=True, class_path=repl_classpath(**kw_args))
    init_clojure_runtime()
    init_libpy_embedded()
    resolve_call_fn("libpython-clj2.embedded/start-embedded-repl!")


class GenericJavaObj:
    __str__ = javabridge.make_method("toString", "()Ljava/lang/String;")
    get_class = javabridge.make_method("getClass", "()Ljava/lang/Class;")
    __repl__ = javabridge.make_method("toString", "()Ljava/lang/String;")
    def __init__(self, jobj):
        self.o = jobj


def longCast(jobj):
    "Cast a java object to a primitive long value."
    return javabridge.static_call("clojure/lang/RT", "longCast",
                                  "(Ljava/lang/Object;)J", jobj)


def to_ptr(pyobj):
    """Create a tech.v3.datatype.ffi.Pointer java object from a python object.  This
    allows you to pass python objects directly into libpython-clj2.python-derived
    pathways (such as ->jvm).  If java is going to hold onto the python data for
    a long time and it will fall out of Python scope  object should be
    'incref-tracked' - 'libpython-clj2.python.ffi/incref-track-pyobject'."""
    return javabridge.static_call("tech/v3/datatype/ffi/Pointer", "constructNonZero",
                                  "(J)Ltech/v3/datatype/ffi/Pointer;", id(pyobj))
