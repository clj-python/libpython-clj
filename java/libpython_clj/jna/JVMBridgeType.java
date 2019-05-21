package libpython_clj.jna;

import com.sun.jna.*;
import java.util.*;


public class JVMBridgeType extends Structure {
  // obj-HEAD
  public long ob_refcnt;
  public Pointer ob_type;
  //Instance data
  public long jvm_interpreter_handle;
  public long jvm_handle;


  public static class ByReference extends JVMBridgeType implements Structure.ByReference {}
  public static class ByValue extends JVMBridgeType implements Structure.ByValue {}
  public JVMBridgeType () {}
  public JVMBridgeType (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "obj_refcnt", "ob_type", "jvm_interpreter_handle", "jvm_handle" }); }
}
