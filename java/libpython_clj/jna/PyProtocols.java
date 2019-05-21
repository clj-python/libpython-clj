package libpython_clj.jna;


import com.sun.jna.*;
import java.util.*;


public class PyProtocols
{
  public static class PyBufferProtocol extends Structure {
    public CFunction.bf_getbuffer bf_getbuffer;
    public CFunction.bf_releasebuffer bf_releasebuffer;

    public static class ByReference extends PyBufferProtocol implements Structure.ByReference {}
    public static class ByValue extends PyBufferProtocol implements Structure.ByValue {}
    public PyBufferProtocol () {}
    public PyBufferProtocol (Pointer p ) { super(p); read(); }
    protected List getFieldOrder() { return Arrays.asList(new String[]
      { "bf_getbuffer", "buf_releasebuffer"}); }
  }
}
