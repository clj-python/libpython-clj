package libpython_clj.jna;


import com.sun.jna.*;


public interface JVMBridge
{
  public Pointer getAttr(String name);
  public void setAttr(String name, Pointer val);
  public String[] dir();
  public Object interpreter();
  public Object wrappedObject();
}
