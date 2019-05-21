package libpython_clj.jna;


import com.sun.jna.*;


public class CFunction {
  public static interface KeyWordFunction extends Callback {
    Pointer pyinvoke(Pointer self, Pointer tuple_args, Pointer kw_args);
  }

  public static interface TupleFunction extends Callback {
    Pointer pyinvoke(Pointer self, Pointer tuple_args);
  }

  public static interface NoArgFunction extends Callback {
    Pointer pyinvoke(Pointer self);
  }

  public static interface tp_new extends Callback {
    Pointer pyinvoke(PyTypeObject type, Pointer args, Pointer kwds);
  }

  public static interface tp_init extends KeyWordFunction {
  }

  public static interface tp_dealloc extends Callback {
    void pyinvoke(Pointer self);
  }

  public static interface tp_att_getter extends Callback {
    Pointer pyinvoke(Pointer self, Pointer closure);
  }

  public static interface tp_att_setter extends Callback {
    Pointer pyinvoke(Pointer self, Pointer value, Pointer closure);
  }

  public static interface tp_getattr extends Callback {
    Pointer pyinvoke(Pointer self, String attr_name);
  }

  public static interface tp_setattr extends Callback {
    int pyinvoke(Pointer self, String attr_name, Pointer val);
  }

  public static interface tp_getattro extends Callback {
    Pointer pyinvoke(Pointer self, Pointer attr_name);
  }

  public static interface tp_setattro extends Callback {
    int pyinvoke(Pointer self, Pointer attr_name, Pointer val);
  }

  public static interface tp_richcompare extends Callback {
    Pointer pyinvoke(Pointer self, Pointer other, int comp_type);
  }

  public static interface tp_hash extends Callback {
    long pyinvoke(Pointer self );
  }

  public static interface bf_getbuffer extends Callback {
    int pyinvoke(Pointer self, PyBuffer item, int flags);
  }

  public static interface bf_releasebuffer extends Callback {
    void pyinvoke(Pointer self, PyBuffer item);
  }
}
