package libpython_clj.jna;


import com.sun.jna.*;


public class CFunction {
  public static interface KeyWordFunction extends Callback {
    PyObject pyinvoke(PyObject self, PyObject tuple_args, PyObject kw_args);
  }

  public static interface TupleFunction extends Callback {
    PyObject pyinvoke(PyObject self, PyObject tuple_args);
  }

  public static interface NoArgFunction extends Callback {
    PyObject pyinvoke(PyObject self);
  }

  public static interface tp_new extends Callback {
    PyObject pyinvoke(PyTypeObject type, PyObject args, PyObject kwds);
  }

  public static interface tp_init extends Callback {
    PyObject pyinvoke(PyObject self, PyObject tuple_args, PyObject kw_args);
  }

  public static interface tp_dealloc extends Callback {
    void pyinvoke(PyObject self);
  }

  public static interface tp_free extends Callback {
    void pyinvoke(Pointer item);
  }

  public static interface tp_att_getter extends Callback {
    PyObject pyinvoke(PyObject self, PyObject closure);
  }

  public static interface tp_att_setter extends Callback {
    PyObject pyinvoke(PyObject self, PyObject value, PyObject closure);
  }

  public static interface tp_getattr extends Callback {
    PyObject pyinvoke(PyObject self, String attr_name);
  }

  public static interface tp_setattr extends Callback {
    int pyinvoke(PyObject self, String attr_name, PyObject val);
  }

  public static interface tp_getattro extends Callback {
    PyObject pyinvoke(PyObject self, PyObject attr_name);
  }

  public static interface tp_setattro extends Callback {
    int pyinvoke(PyObject self, PyObject attr_name, PyObject val);
  }

  public static interface tp_richcompare extends Callback {
    PyObject pyinvoke(PyObject self, PyObject other, int comp_type);
  }

  public static interface tp_hash extends Callback {
    long pyinvoke(PyObject self );
  }

  public static interface bf_getbuffer extends Callback {
    int pyinvoke(PyObject self, PyBuffer item, int flags);
  }

  public static interface bf_releasebuffer extends Callback {
    void pyinvoke(PyObject self, PyBuffer item);
  }
}
