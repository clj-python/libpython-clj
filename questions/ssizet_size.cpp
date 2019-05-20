#include "Python.h"
#include <cstdio>

using namespace std;


int main( int c, char** v)
{
  printf("%ld\n", static_cast<long>(sizeof(Py_ssize_t)));
  return 0;
}
