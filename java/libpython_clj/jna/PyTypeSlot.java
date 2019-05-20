package libpython_clj.jna;


import com.sun.jna.*;
import java.util.*;


public class PyTypeSlot extends Structure {
  public int slot;
  public Pointer pfunc;

  public static class ByReference extends PyTypeSlot implements Structure.ByReference {}
  public static class ByValue extends PyTypeSlot implements Structure.ByValue {}
  public PyTypeSlot () {}
  public PyTypeSlot (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "slot", "pfunc"}); }
}
