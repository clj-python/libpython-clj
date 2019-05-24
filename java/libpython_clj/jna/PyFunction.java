package libpython_clj.jna;


import java.util.*;
import clojure.lang.IFn;


public interface PyFunction
{
  Object invokeKeyWords(List tupleArgs, Map keywordArgs);
}
