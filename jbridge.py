import subprocess
import javabridge

try:
    from collections.abc import Callable  # noqa
except ImportError:
    from collections import Callable

def deps_classpath(*clj_args):
    return subprocess.check_output(['clojure'] + list(clj_args) + ['-Spath']).decode("utf-8").strip().split(':')


def repl_classpath():
    return deps_classpath("-Sdeps", '{:deps {nrepl/nrepl {:mvn/version "0.8.3"} cider/cider-nrepl {:mvn/version "0.25.5"}}}')


def start_vm():
    javabridge.start_vm(run_headless=True, class_path=deps_classpath())


def start_repl_vm():
    javabridge.start_vm(run_headless=True, class_path=repl_classpath())


def init_clojure():
    start_vm()
    javabridge.static_call("clojure/lang/RT", "init", "()V")
    return True


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


def symbol(sym_name):
    return javabridge.static_call("clojure/lang/Symbol", "intern",
                                  "(Ljava/lang/String;)Lclojure/lang/Symbol;", sym_name)

__REQUIRE_FN = None

def require(ns_name):
    if not __REQUIRE_FN:
        _REQUIRE_FN = CLJFn("clojure.core", "require")
    return _REQUIRE_FN(symbol(ns_name))


def init_libpy_embedded():
    embed_fn = CLJFn("libpython-clj2.python", "initialize-embedded!")
    return embed_fn()


class GenericJavaObj:
    __str__ = javabridge.make_method("toString", "()Ljava/lang/String;")
    get_class = javabridge.make_method("getClass", "()Ljava/lang/Class;")
    def __init__(self, jobj):
        self.o = jobj


def longCast(jobj):
    return javabridge.static_call("clojure/lang/RT", "longCast",
                                  "(Ljava/lang/Object;)J", jobj)


def to_ptr(pyobj):
    return javabridge.static_call("tech/v3/datatype/ffi/Pointer", "constructNonZero",
                                  "(J)Ltech/v3/datatype/ffi/Pointer;", id(pyobj))


def init_clojure_repl():
    start_repl_vm()
    require("libpython-clj2.python")
    init = CLJFn("libpython-clj2.python", "initialize-embedded!")
    init()
    repl_fn = CLJFn("libpython-clj2.python", "start-embedded-repl!")
    # This will no return; the GIL will be released and a port will be opened up
    # that you can connect to for full cider lovin.
    repl_fn()
