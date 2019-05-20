package libpython_clj.jna;


import com.sun.jna.*;
import java.util.*;


public class CFunction {
  public static interface KeyWordFunction extends Callback {
    Pointer pyinvoke(Pointer self, Pointer tuple_args, Pointer kw_args);
  }

  public static interface TupleFunction extends Callback {
    Pointer pyinvoke(Pointer self, Pointer tuple_args);
  }
}
