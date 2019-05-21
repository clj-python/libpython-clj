package libpython_clj.jna;


import com.sun.jna.*;
import java.util.*;

// typedef PyObject * (*unaryfunc)(PyObject *);
// typedef PyObject * (*binaryfunc)(PyObject *, PyObject *);
// typedef PyObject * (*ternaryfunc)(PyObject *, PyObject *, PyObject *);
// typedef int (*inquiry)(PyObject *);
// typedef Py_ssize_t (*lenfunc)(PyObject *);
// typedef PyObject *(*ssizeargfunc)(PyObject *, Py_ssize_t);
// typedef PyObject *(*ssizessizeargfunc)(PyObject *, Py_ssize_t, Py_ssize_t);
// typedef int(*ssizeobjargproc)(PyObject *, Py_ssize_t, PyObject *);
// typedef int(*ssizessizeobjargproc)(PyObject *, Py_ssize_t, Py_ssize_t, PyObject *);
// typedef int(*objobjargproc)(PyObject *, PyObject *, PyObject *);
// typedef int (*getbufferproc)(PyObject *, Py_buffer *, int);
// typedef void (*releasebufferproc)(PyObject *, Py_buffer *);
/* Maximum number of dimensions */
// #define PyBUF_MAX_NDIM 64

// /* Flags for getting buffers */
// #define PyBUF_SIMPLE 0
// #define PyBUF_WRITABLE 0x0001
// /*  we used to include an E, backwards compatible alias  */
// #define PyBUF_WRITEABLE PyBUF_WRITABLE
// #define PyBUF_FORMAT 0x0004
// #define PyBUF_ND 0x0008
// #define PyBUF_STRIDES (0x0010 | PyBUF_ND)
// #define PyBUF_C_CONTIGUOUS (0x0020 | PyBUF_STRIDES)
// #define PyBUF_F_CONTIGUOUS (0x0040 | PyBUF_STRIDES)
// #define PyBUF_ANY_CONTIGUOUS (0x0080 | PyBUF_STRIDES)
// #define PyBUF_INDIRECT (0x0100 | PyBUF_STRIDES)

// #define PyBUF_CONTIG (PyBUF_ND | PyBUF_WRITABLE)
// #define PyBUF_CONTIG_RO (PyBUF_ND)

// #define PyBUF_STRIDED (PyBUF_STRIDES | PyBUF_WRITABLE)
// #define PyBUF_STRIDED_RO (PyBUF_STRIDES)

// #define PyBUF_RECORDS (PyBUF_STRIDES | PyBUF_WRITABLE | PyBUF_FORMAT)
// #define PyBUF_RECORDS_RO (PyBUF_STRIDES | PyBUF_FORMAT)

// #define PyBUF_FULL (PyBUF_INDIRECT | PyBUF_WRITABLE | PyBUF_FORMAT)
// #define PyBUF_FULL_RO (PyBUF_INDIRECT | PyBUF_FORMAT)


// #define PyBUF_READ  0x100
// #define PyBUF_WRITE 0x200

// /* End buffer interface */
// #endif /* Py_LIMITED_API */

// typedef int (*objobjproc)(PyObject *, PyObject *);
// typedef int (*visitproc)(PyObject *, void *);
// typedef int (*traverseproc)(PyObject *, visitproc, void *);

// typedef struct {
//     /* Number implementations must check *both*
//        arguments for proper type and implement the necessary conversions
//        in the slot functions themselves. */

//     binaryfunc nb_add;
//     binaryfunc nb_subtract;
//     binaryfunc nb_multiply;
//     binaryfunc nb_remainder;
//     binaryfunc nb_divmod;
//     ternaryfunc nb_power;
//     unaryfunc nb_negative;
//     unaryfunc nb_positive;
//     unaryfunc nb_absolute;
//     inquiry nb_bool;
//     unaryfunc nb_invert;
//     binaryfunc nb_lshift;
//     binaryfunc nb_rshift;
//     binaryfunc nb_and;
//     binaryfunc nb_xor;
//     binaryfunc nb_or;
//     unaryfunc nb_int;
//     void *nb_reserved;  /* the slot formerly known as nb_long */
//     unaryfunc nb_float;

//     binaryfunc nb_inplace_add;
//     binaryfunc nb_inplace_subtract;
//     binaryfunc nb_inplace_multiply;
//     binaryfunc nb_inplace_remainder;
//     ternaryfunc nb_inplace_power;
//     binaryfunc nb_inplace_lshift;
//     binaryfunc nb_inplace_rshift;
//     binaryfunc nb_inplace_and;
//     binaryfunc nb_inplace_xor;
//     binaryfunc nb_inplace_or;

//     binaryfunc nb_floor_divide;
//     binaryfunc nb_true_divide;
//     binaryfunc nb_inplace_floor_divide;
//     binaryfunc nb_inplace_true_divide;

//     unaryfunc nb_index;

//     binaryfunc nb_matrix_multiply;
//     binaryfunc nb_inplace_matrix_multiply;
// } PyNumberMethods;

// typedef struct {
//     lenfunc sq_length;
//     binaryfunc sq_concat;
//     ssizeargfunc sq_repeat;
//     ssizeargfunc sq_item;
//     void *was_sq_slice;
//     ssizeobjargproc sq_ass_item;
//     void *was_sq_ass_slice;
//     objobjproc sq_contains;

//     binaryfunc sq_inplace_concat;
//     ssizeargfunc sq_inplace_repeat;
// } PySequenceMethods;

// typedef struct {
//     lenfunc mp_length;
//     binaryfunc mp_subscript;
//     objobjargproc mp_ass_subscript;
// } PyMappingMethods;

// typedef struct {
//     unaryfunc am_await;
//     unaryfunc am_aiter;
//     unaryfunc am_anext;
// } PyAsyncMethods;

// typedef struct {
//      getbufferproc bf_getbuffer;
//      releasebufferproc bf_releasebuffer;
// } PyBufferProcs;
// #endif /* Py_LIMITED_API */

// typedef void (*freefunc)(void *);
// typedef void (*destructor)(PyObject *);
// #ifndef Py_LIMITED_API
// /* We can't provide a full compile-time check that limited-API
//    users won't implement tp_print. However, not defining printfunc
//    and making tp_print of a different function pointer type
//    should at least cause a warning in most cases. */
// typedef int (*printfunc)(PyObject *, FILE *, int);
// #endif
// typedef PyObject *(*getattrfunc)(PyObject *, char *);
// typedef PyObject *(*getattrofunc)(PyObject *, PyObject *);
// typedef int (*setattrfunc)(PyObject *, char *, PyObject *);
// typedef int (*setattrofunc)(PyObject *, PyObject *, PyObject *);
// typedef PyObject *(*reprfunc)(PyObject *);
// typedef Py_hash_t (*hashfunc)(PyObject *);
// typedef PyObject *(*richcmpfunc) (PyObject *, PyObject *, int);
// typedef PyObject *(*getiterfunc) (PyObject *);
// typedef PyObject *(*iternextfunc) (PyObject *);
// typedef PyObject *(*descrgetfunc) (PyObject *, PyObject *, PyObject *);
// typedef int (*descrsetfunc) (PyObject *, PyObject *, PyObject *);
// typedef int (*initproc)(PyObject *, PyObject *, PyObject *);
// typedef PyObject *(*newfunc)(struct _typeobject *, PyObject *, PyObject *);
// typedef PyObject *(*allocfunc)(struct _typeobject *, Py_ssize_t);


/**
PyObject size: 16
PyTypeObject size: 400
type.tp_basicsize: 32
type.tp_as_number: 96
type.tp_as_buffer: 160
type.tp_finalize: 392
**/


public class PyTypeObject extends Structure
{
  public long ob_refcnt;
  public Pointer ob_type;
  public long ob_size;
  public Pointer tp_name; /* For printing, in format "<module>.<name>" */

  /* For allocation */
  public long tp_basicsize;
  public long tp_itemsize;

  /* Methods to implement standard operations */

  public CFunction.tp_dealloc tp_dealloc;
  public Pointer tp_print;
  public CFunction.tp_getattr tp_getattr;
  public CFunction.tp_setattr tp_setattr;
  public Pointer tp_as_async; /* formerly known as tp_compare (Python 2)
				 or tp_reserved (Python 3) */
  public CFunction.NoArgFunction tp_repr;

    /* Method suites for standard classes */

  public Pointer tp_as_number;
  public Pointer tp_as_sequence;
  public Pointer tp_as_mapping;

  /* More standard operations (here for binary compatibility) */

  public CFunction.tp_hash tp_hash;
  public CFunction.KeyWordFunction tp_call;
  public CFunction.NoArgFunction tp_str;
  public CFunction.tp_getattro tp_getattro;
  public CFunction.tp_setattro tp_setattro;

  /* Functions to access object as input/output buffer */
  public Pointer tp_as_buffer;

  /* Flags to define presence of optional/expanded features */
  public int tp_flags;

  public Pointer tp_doc; /* Documentation string */

  /* Assigned meaning in release 2.0 */
  /* call function for all accessible objects */
  public Pointer tp_traverse;

  /* delete references to contained objects */
  public Pointer tp_clear;

  /* Assigned meaning in release 2.1 */
  /* rich comparisons */
  public CFunction.tp_richcompare tp_richcompare;

    /* weak reference enabler */
  public long tp_weaklistoffset;

  /* Iterators */
  public CFunction.NoArgFunction tp_iter;
  public CFunction.NoArgFunction tp_iternext;

  /* Attribute descriptor and subclassing stuff */
  public PyMethodDef tp_methods;
  public PyMemberDef tp_members;
  public PyGetSetDef tp_getset;
  public Pointer tp_base;
  public Pointer tp_dict;
  public Pointer tp_descr_get;
  public Pointer tp_descr_set;
  public long tp_dictoffset;
  public CFunction.tp_init tp_init;
  public Pointer tp_alloc;
  public CFunction.tp_new tp_new;
  public Pointer tp_free; /* Low-level free-memory routine */
  public Pointer tp_is_gc; /* For PyObject_IS_GC */
  public Pointer _bases;
  public Pointer tp_mro; /* method resolution order */
  public Pointer tp_cache;
  public Pointer tp_subclasses;
  public Pointer tp_weaklist;
  public Pointer tp_del;

  /* Type attribute cache version tag. Added in version 2.6 */
  public int tp_version_tag;

  public Pointer tp_finalize;

  public static class ByReference extends PyTypeObject implements Structure.ByReference {}
  public static class ByValue extends PyTypeObject implements Structure.ByValue {}
  public PyTypeObject () {}
  public PyTypeObject (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    {
      "ob_refcnt",
      "ob_type",
   "ob_size",
   "tp_name", /* For printing, in format "<module>.<name>" */

  /* For allocation */
   "tp_basicsize",
   "tp_itemsize",

  /* Methods to implement standard operations */

   "tp_dealloc",
   "tp_print",
   "tp_getattr",
   "tp_setattr",
   "tp_as_async", /* formerly known as tp_compare (Python 2)
				 or tp_reserved (Python 3) */
   "tp_repr",

    /* Method suites for standard classes */

   "tp_as_number",
   "tp_as_sequence",
   "tp_as_mapping",

  /* More standard operations (here for binary compatibility) */

   "tp_hash",
   "tp_call",
   "tp_str",
   "tp_getattro",
   "tp_setattro",

  /* Functions to access object as input/output buffer */
   "tp_as_buffer",

  /* Flags to define presence of optional/expanded features */
   "tp_flags",

   "tp_doc", /* Documentation string */

  /* Assigned meaning in release 2.0 */
  /* call function for all accessible objects */
   "tp_traverse",

  /* delete references to contained objects */
   "tp_clear",

  /* Assigned meaning in release 2.1 */
  /* rich comparisons */
   "tp_richcompare",

    /* weak reference enabler */
   "tp_weaklistoffset",

  /* Iterators */
   "tp_iter",
   "tp_iternext",

  /* Attribute descriptor and subclassing stuff */
   "tp_methods",
   "tp_members",
   "tp_getset",
   "tp_base",
   "tp_dict",
   "tp_descr_get",
   "tp_descr_set",
   "tp_dictoffset",
   "tp_init",
   "tp_alloc",
   "tp_new",
   "tp_free", /* Low-level free-memory routine */
   "tp_is_gc", /* For PyObject_IS_GC */
   "_bases",
   "tp_mro", /* method resolution order */
   "tp_cache",
   "tp_subclasses",
   "tp_weaklist",
   "tp_del",

  /* Type attribute cache version tag. Added in version 2.6 */
   "tp_version_tag",

   "tp_finalize"
    }); }
}
