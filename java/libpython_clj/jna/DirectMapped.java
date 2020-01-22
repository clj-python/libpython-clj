package libpython_clj.jna;

import com.sun.jna.Pointer;


public class DirectMapped
{
  public static native void Py_IncRef(Pointer ptr);
  public static native void Py_DecRef(Pointer ptr);
  public static native Pointer PyFloat_FromDouble(double y);
  public static native double PyFloat_AsDouble(Pointer val);
  //FIXME - how to change signatures if 32 bit.
  public static native Pointer PyTuple_New(long size);
  public static native Pointer PyTuple_GetItem(Pointer p, long size);
  public static native int PyTuple_SetItem(Pointer p, long pos, Pointer o);
  public static native Pointer PyErr_Occurred();
  public static native Pointer PyObject_CallObject(Pointer callable, Pointer argtuple);
  public static native Pointer PyObject_Call(Pointer callable,
					     Pointer argtuple,
					     Pointer kwargs);
  public static native int PyObject_HasAttrString(Pointer obj, String attrName);
  public static native Pointer PyObject_GetAttrString(Pointer obj, String attrName);
  public static native int PyCallable_Check(Pointer p);
}
