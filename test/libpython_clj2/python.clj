(ns libpython-clj2.python
  (:require [libpython-clj2.python.info :as py-info]
            [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.dechunk-map :refer [dechunk-map]]
            [libpython-clj.python.windows :as win]
            [tech.v3.datatype.ffi :as dtype-ffi]
            [clojure.tools.logging :as log]))



(defn initialize!
    "Initialize the python library.  If library path is provided, then the system
  attempts to execute a simple python program and have python return system info.


  Returns either `:ok` in which case the initialization completed successfully or
  `:already-initialized` in which case we detected that python has already been
  initialized vi Py_IsInitialized? and we do nothing more.

  Options:

  * `:library-path` - Library path of the python library to use.
  * `:program-name` - Optional -- will show up in error messages from python.
  * `:no-io-redirect?` - True if you don't want python stdout and stderr redirection
     to *out* and *err*.
  * `:python-executable` - The python executable to use to find system information.
  * `:python-home` - Python home directory.  The system first uses this variable, then
     the environment variable PYTHON_HOME, and finally information returned from
     python system info.
  * `:signals?` - defaults to false - true if you want python to initialized signas.
     Be aware that the JVM itself uses quite a few signals (SIGSEGV, for instance)
     during it's normal course of operation.  For more information see:
       * [used signals](https://docs.oracle.com/javase/10/troubleshoot/handle-signals-and-exceptions.htm#JSTGD356)
       * [signal-chaining](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/signal-chaining.html)"
  [& [{:keys [windows-anaconda-activate-bat
              library-path]} options]]
  (if-not (and (py-ffi/library-loaded?)
                 (= 1 (py-ffi/Py_IsInitialized)))
    (let [info (py-info/detect-startup-info options)
          _ (log/infof "Startup info %s" info)
          libname (->> (concat (when library-path [library-path]) (:libnames info))
                       (dechunk-map identity)
                       (filter #(try
                                  (boolean (dtype-ffi/load-library %))
                                  (catch Throwable e false)))
                       (first))]
      (log/infof "Loading python library: %s" libname)
      (py-ffi/initialize! libname (:python-home info) options)
      (when-not (nil? windows-anaconda-activate-bat)
        (win/setup-windows-conda! windows-anaconda-activate-bat
                                  py-ffi/run-simple-string))
      :ok)
    :already-initialized))
