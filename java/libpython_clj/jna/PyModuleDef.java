package libpython_clj.jna;


import com.sun.jna.*;
import java.util.*;


public class PyModuleDef extends Structure {
  // obj-HEAD
  public long ob_refcnt;
  public Pointer ob_type;
  // module base
  public Pointer m_init;
  public long m_index;
  public Pointer m_copy;
  //Instance data
  public Pointer m_name;
  public Pointer m_doc;
  public long m_size;
  public Pointer m_methods;
  public Pointer m_slots;
  public Pointer m_traverse;
  public Pointer m_clear;
  public Pointer m_free;


  public static class ByReference extends PyModuleDef implements Structure.ByReference {}
  public static class ByValue extends PyModuleDef implements Structure.ByValue {}
  public PyModuleDef () {}
  public PyModuleDef (Pointer p ) { super(p); read(); }
  protected List getFieldOrder() { return Arrays.asList(new String[]
    { "obj_refcnt", "ob_type",
      "m_init",
      "m_index",
      "m_copy",
      "m_name",
      "m_doc",
      "m_size",
      "m_methods",
      "m_slots",
      "m_traverse",
      "m_clear",
      "m_free"
       }); }
}
