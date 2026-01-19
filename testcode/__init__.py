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


complex_fn_testcases = {
    "complex_fn(1, 2, c=10, d=10, e=10)": complex_fn(1, 2, c=10, d=10, e=10),
    "complex_fn(1, 2, 10, 11, 12, d=10, e=10)": complex_fn(
        1, 2, 10, 11, 12, d=10, e=10
    ),
}
