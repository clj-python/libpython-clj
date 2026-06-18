import functools


class WithObjClass:
    def __init__(self, suppress, fn_list):
        self.suppress = suppress
        self.fn_list = fn_list

    def __enter__(self):
        self.fn_list.append("enter")
        return self  # Return self so methods can be called on the bound variable

    def doit_noerr(self):
        return 1

    def doit_err(self):
        raise Exception("Spam", "Eggs")

    def __exit__(self, ex_type, ex_val, ex_traceback):
        self.fn_list.append("exit: " + str(ex_val))
        return self.suppress


class FileWrapper:
    """Context manager where __enter__ returns a different object"""

    def __init__(self, content):
        self.content = content

    def __enter__(self):
        # Return a different object with the content
        import io

        return io.StringIO(self.content)

    def __exit__(self, *args):
        return False


def for_iter(arg):
    retval = []
    for item in arg:
        retval.append(item)
    return retval


def calling_custom_clojure_fn(arg):
    return arg.clojure_fn()


def complex_fn(a, b, c: str = 5, *args, d=10, **kwargs):
    return {"a": a, "b": b, "c": c, "args": args, "d": d, "kwargs": kwargs}


def defaults_fn(top=".", topdown=True, onerror=None, *, follow_symlinks=False, dir_fd=None):
    """Function with Python defaults for metadata tests."""
    return top, topdown, onerror, follow_symlinks, dir_fd


def default_type_fn(dtype=int):
    """Function with a Python object default for metadata tests."""
    return dtype


def kw_default_type_fn(*, dtype=int):
    """Function with a keyword-only Python object default for metadata tests."""
    return dtype


complex_fn_testcases = {
    "complex_fn(1, 2, c=10, d=10, e=10)": complex_fn(1, 2, c=10, d=10, e=10),
    "complex_fn(1, 2, 10, 11, 12, d=10, e=10)": complex_fn(
        1, 2, 10, 11, 12, d=10, e=10
    ),
}


class BadStr:
    def __repr__(self):
        raise ValueError("boom repr")
    def __str__(self):
        raise ValueError("boom str")


class WeirdStr:
    def __repr__(self):
        return "x" * 40


class HugeReprModel:
    def __init__(self, n_layers=300):
        self.n_layers = n_layers

    def __repr__(self):
        lines = ["GnarlyNet("]
        for i in range(self.n_layers):
            lines.append(f"  (layer{i}): Linear(in_features=4096, out_features=4096, bias=True)")
            lines.append(f"  (act{i}): GELU(approximate='none')")
            lines.append(f"  (drop{i}): Dropout(p=0.1, inplace=False)")
        lines.append(")")
        return "\n".join(lines)
    __str__ = __repr__


_bad = BadStr()
_weird = WeirdStr()
_sentinel = object()
_partial = functools.partial(int, 0)
_huge = HugeReprModel(300)


def f_class(x=int):
    return x

def f_lambda(x=lambda a: a):
    return x

def f_badstr(x=_bad):
    return x

def f_weird(x=_weird):
    return x

def f_sentinel(x=_sentinel):
    return x

def f_partial(x=_partial):
    return x

def f_nested_opaque(x=(int, str)):
    return x

def f_huge(model=_huge):
    return model

def f_kw_huge(*, model=_huge):
    return model

def f_mixed(a, b=1, c=int, *, d=_sentinel, e=2):
    return (a, b, c, d, e)
