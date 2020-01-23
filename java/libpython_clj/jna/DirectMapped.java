package libpython_clj.jna;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.ByteBuffer;


public class DirectMapped
{
  public static native void Py_IncRef(Pointer ptr);
  public static native void Py_DecRef(Pointer ptr);
  public static native int PyGILState_Check();
  public static native void PyEval_RestoreThread(Pointer tstate);
  public static native Pointer PyEval_SaveThread();
  public static native Pointer PyFloat_FromDouble(double y);
  public static native double PyFloat_AsDouble(Pointer val);
  public static native Pointer PyLong_FromLongLong(long v);
  public static native long PyLong_AsLongLong(Pointer v);
  public static native int PyDict_Next(Pointer p, LongByReference ppos,
				       PointerByReference pkey,
				       PointerByReference pvalue);
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
  public static native Pointer PyUnicode_AsUTF8AndSize(Pointer pyobj,
						       LongByReference reference);
  public static native Pointer PyUnicode_Decode(ByteBuffer bytedata,
						long numchars, //size-t
						String encoding,
						String flags);
  public static native int PySequence_Check(Pointer val);
  //returns size-t
  public static native long PySequence_Length(Pointer val);
  //takes size-t
  public static native Pointer PySequence_GetItem(Pointer val, long idx);
}
