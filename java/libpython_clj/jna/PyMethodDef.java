package libpython_clj.jna;

import com.sun.jna.*;
import java.util.*;


public class PyMethodDef extends Structure {

  // We keep these as pointers because the methoddef has to live forever
  // so we have to manually ensure the name and doc ptrs also live forever.
  public Pointer ml_name;
  // This is callback but because it can be several different distinct classes
  // it has to be opaque on the object.  Thus marshalling it to the actual function
  // is a dynamic process that needs to look at the ml_flags to work.
  public Pointer ml_meth;
  public int ml_flags;
  public Pointer ml_doc;


  public static class ByReference extends PyMethodDef implements Structure.ByReference
  {
    public ByReference (Pointer p) { super(p); read(); }
  }
  public static class ByValue extends PyMethodDef implements Structure.ByValue {}
  public PyMethodDef () {}
  public PyMethodDef (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "ml_name", "ml_meth", "ml_flags", "ml_doc" }); }
}
