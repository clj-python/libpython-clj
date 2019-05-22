package libpython_clj.jna;


import com.sun.jna.*;
import java.util.*;


public class PyObject extends Structure {
  public long ob_refcnt;
  // The Type object is kept opaque to avoid too much unnecessary marshalling
  public Pointer ob_type;

  public static class ByReference extends PyObject implements Structure.ByReference {}
  public static class ByValue extends PyObject implements Structure.ByValue {}
  public PyObject () {}
  public PyObject (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "ob_refcnt", "ob_type" }); }
}
