package libpython_clj.jna;


import com.sun.jna.*;
import java.util.*;


public class JVMBridgeType extends PyObject {
  //Instance data
  public long jvm_interpreter_handle;
  public long jvm_handle;


  public static class ByReference extends JVMBridgeType implements Structure.ByReference {}
  public static class ByValue extends JVMBridgeType implements Structure.ByValue {}
  public JVMBridgeType () {}
  public JVMBridgeType (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() {
    List retval = new ArrayList(super.getFieldOrder());
    retval.addAll( Arrays.asList(new String[]
      { "jvm_interpreter_handle", "jvm_handle" }));
    return retval;
  }
}
