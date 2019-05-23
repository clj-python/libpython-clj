package libpython_clj.jna;


import com.sun.jna.*;


public interface JVMBridge
{
  public PyObject getAttr(String name);
  public void setAttr(String name, PyObject val);
  public String[] dir();
  public Object interpreter();
  public Object wrappedObject();
}
