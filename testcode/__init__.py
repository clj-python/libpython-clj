class WithObjClass:
    def __init__(self, suppress):
        self.suppress = suppress
    def __enter__(self):
        print( "Entering test obj")
    def doit_noerr(self):
        return 1
    def doit_err(self):
        raise Exception("Spam", "Eggs")
    def __exit__(self, ex_type, ex_val, ex_traceback):
        print( "Exiting: " + str(ex_type) + str(ex_val) + str(ex_traceback))
        return self.suppress
