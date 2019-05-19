package libpython_clj.jna;

import com.sun.jna.*;
import java.util.*;


public class PyMethodDef extends Structure {

  public Pointer ml_name; 
  public Pointer ml_meth;
  public int ml_flags;
  public Pointer ml_doc;


  public static class ByReference extends PyMethodDef implements Structure.ByReference {}
  public static class ByValue extends PyMethodDef implements Structure.ByValue {}
  public PyMethodDef () {}
  public PyMethodDef (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "ml_name", "ml_meth", "ml_flags", "ml_doc" }); }
}
