#include "Python.h"
#include <cstdio>
#include <cstddef>

using namespace std;


int main(int c, char** v)
{
  printf(
   "PyObject size: %ld\n"
   "PyTypeObject size: %ld\n"
   "type.tp_basicsize: %ld\n"
   "type.tp_as_number: %ld\n"
   "type.tp_as_buffer: %ld\n"
   "type.tp_finalize: %ld\n"
   "py_hash_t: %ld\n"
   "Py_TPFLAGS_DEFAULT: %ld\n"
   ,
   sizeof(PyObject),
   sizeof(PyTypeObject),
   offsetof(PyTypeObject, tp_basicsize),
   offsetof(PyTypeObject, tp_as_number),
   offsetof(PyTypeObject, tp_as_buffer),
   offsetof(PyTypeObject, tp_finalize),
   sizeof(Py_hash_t),
   Py_TPFLAGS_DEFAULT
    );
}
