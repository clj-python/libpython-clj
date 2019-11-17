class WithObjClass:
    def __init__(self, suppress, fn_list):
        self.suppress = suppress
        self.fn_list = fn_list
    def __enter__(self):
        self.fn_list.append("enter")
    def doit_noerr(self):
        return 1
    def doit_err(self):
        raise Exception("Spam", "Eggs")
    def __exit__(self, ex_type, ex_val, ex_traceback):
        self.fn_list.append("exit: " + str(ex_val))
        return self.suppress


def for_iter(arg):
    retval = []
    for item in arg:
        retval.append(item)
    return retval
