package libpython_clj.jna;

import com.sun.jna.*;
import java.util.*;


public class PyGetSetDef extends Structure {

  public Pointer name;
  public CFunction.tp_att_getter get;
  public CFunction.tp_att_setter set;
  public Pointer doc;
  public Pointer closure;


  public static class ByReference extends PyGetSetDef implements Structure.ByReference {}
  public static class ByValue extends PyGetSetDef implements Structure.ByValue {}
  public PyGetSetDef () {}
  public PyGetSetDef (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "name", "get", "set", "doc", "closure" }); }
}
