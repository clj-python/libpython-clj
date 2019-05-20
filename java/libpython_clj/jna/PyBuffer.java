package libpython_clj.jna;


import com.sun.jna.*;
import java.util.*;


// typedef struct bufferinfo {
//     void *buf;
//     PyObject *obj;        /* owned reference */
//     Py_ssize_t len;
//     Py_ssize_t itemsize;  /* This is Py_ssize_t so it can be
//                              pointed to by strides in simple case.*/
//     int readonly;
//     int ndim;
//     char *format;
//     Py_ssize_t *shape;
//     Py_ssize_t *strides;
//     Py_ssize_t *suboffsets;
//     void *internal;
// } Py_buffer;


public class PyBuffer extends Structure {
  public Pointer buf;
  public Pointer obj;
  public long len;
  public long itemsize;


  public int readonly;
  public int ndim;
  public Pointer format;
  public Pointer shape;
  public Pointer strides;
  public Pointer suboffsets;
  public Pointer internal;

  public static class ByReference extends PyBuffer implements Structure.ByReference {}
  public static class ByValue extends PyBuffer implements Structure.ByValue {}
  public PyBuffer () {}
  public PyBuffer (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "buf", "obj", "len", "itemsize", "readonly", "ndim", "format",
      "shape", "strides", "suboffsets", "internal"}); }
}
