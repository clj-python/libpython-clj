package libpython_clj.jna;

import com.sun.jna.*;
import java.util.*;


public class PyMemberDef extends Structure {

  public Pointer name;
  public int type;
  public long offset;
  public int flags;
  public Pointer doc;


  public static class ByReference extends PyMemberDef implements Structure.ByReference {}
  public static class ByValue extends PyMemberDef implements Structure.ByValue {}
  public PyMemberDef () {}
  public PyMemberDef (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "name", "type", "offset", "flags", "doc" }); }
}
