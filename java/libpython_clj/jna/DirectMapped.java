package libpython_clj.jna;

import com.sun.jna.Pointer;


public class DirectMapped
{
  public static native void Py_IncRef(Pointer ptr);
  public static native void Py_DecRef(Pointer ptr);
}
