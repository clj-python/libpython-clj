package libpython_clj.jna;


import com.sun.jna.*;
import java.util.*;


public class PyTypeSpec extends Structure {
  public Pointer name; /* const char* */
  public int basicsize;
  public int itemsize;
  public int flags;
  public Pointer slots; /* PyType_Slot *, terminated by slot==0. */
  public static class ByReference extends PyTypeSpec implements Structure.ByReference {}
  public static class ByValue extends PyTypeSpec implements Structure.ByValue {}
  public PyTypeSpec () {}
  public PyTypeSpec (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "name", "basicsize", "itemsize", "flags", "slots"}); }
}
