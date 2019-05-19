package libpython_clj.jna;


import com.sun.jna.*;
import java.util.*;


public class PyComplex extends Structure {

  public double real;
  public double imag;

  public static class ByReference extends PyComplex implements Structure.ByReference {}
  public static class ByValue extends PyComplex implements Structure.ByValue
  {
    ByValue() {}
    ByValue(PyComplex p) {
      real = p.real;
      imag = p.imag;
    }
  }
  public PyComplex () {}
  public PyComplex (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "real", "imag" }); }
}
