(ns libpython-clj2.python.info
  "Python system information.  Uses the installed python executable to find out
  various details about how python is expecting to run."
  (:require [clojure.java.shell :as sh]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log])
  (:import [java.nio.file Paths]))


(def ^:private default-python-executables
  ["python3" "python3.6" "python3.7" "python3.8" "python3.9" "python"])


(defn python-system-info
  "An information map about the Python system information provided
  by a Python executable (string).

  :platform (operating system information)
  :prefix
  A string giving the site-specific directory prefix where the platform independent Python files are installed; by default, this is the string '/usr/local'. This can be set at build time with the --prefix argument to the configure script. The main collection of Python library modules is installed in the directory prefix/lib/pythonX.Y while the platform independent header files (all except pyconfig.h) are stored in prefix/include/pythonX.Y, where X.Y is the version number of Python, for example 3.2.
  Note If a virtual environment is in effect, this value will be changed in site.py to point to the virtual environment. The value for the Python installation will still be available, via base_prefix.

  :base-prefix
  Set during Python startup, before site.py is run, to the same value as prefix. If not running in a virtual environment, the values will stay the same; if site.py finds that a virtual environment is in use, the values of prefix and exec_prefix will be changed to point to the virtual environment, whereas base_prefix and base_exec_prefix will remain pointing to the base Python installation (the one which the virtual environment was created from).

  :executable
  A string giving the absolute path of the executable binary for the Python interpreter, on systems where this makes sense. If Python is unable to retrieve the real path to its executable, sys.executable will be an empty string or None.

  :exec-prefix
  A string giving the site-specific directory prefix where the platform-dependent Python files are installed; by default, this is also '/usr/local'. This can be set at build time with the --exec-prefix argument to the configure script. Specifically, all configuration files (e.g. the pyconfig.h header file) are installed in the directory exec_prefix/lib/pythonX.Y/config, and shared library modules are installed in exec_prefix/lib/pythonX.Y/lib-dynload, where X.Y is the version number of Python, for example 3.2.
  Note If a virtual environment is in effect, this value will be changed in site.py to point to the virtual environment. The value for the Python installation will still be available, via base_exec_prefix.

  :base-exec-prefix
  Set during Python startup, before site.py is run, to the same value as exec_prefix. If not running in a virtual environment, the values will stay the same; if site.py finds that a virtual environment is in use, the values of prefix and exec_prefix will be changed to point to the virtual environment, whereas base_prefix and base_exec_prefix will remain pointing to the base Python installation (the one which the virtual environment was created from).

  :version
  (list python-major python-minor python-micro)"
  [executable]
  (let [{:keys [out err exit]}
        (sh/sh executable "-c" "import sys, json;
print(json.dumps(
{'platform':          sys.platform,
  'prefix':           sys.prefix,
  'base-prefix':      sys.base_prefix,
  'executable':       sys.executable,
  'base-exec-prefix': sys.base_exec_prefix,
  'exec-prefix':      sys.exec_prefix,
  'version':          list(sys.version_info)[:3]}))")]
    (when (= 0 exit)
      (json/read-str out :key-fn keyword))))


(defn find-python-info
  [& [{:keys [python-executable]}]]
  (->> (if python-executable
         [python-executable]
         default-python-executables)
       (map #(try
               (python-system-info %)
               (catch Throwable e nil)))
       (remove nil?)
       (first)))


(defn find-python-home
  [system-info & [{:keys [python-home]}]]
  (cond
    python-home
    python-home
    (seq (System/getenv "PYTHONHOME"))
    (System/getenv "PYTHONHOME")
    :else
    (:base-prefix system-info)))


(defn java-library-path-addendum
  [python-home]
  (when python-home
    (-> (Paths/get python-home
                   (into-array String ["lib"]))
        (.toString))))


(defn detect-startup-info
  [& [{:keys [python-executable library-path] :as options}]]
  (if python-executable
    (log/infof "Detecting startup info for Python executable %s" python-executable)
    (log/info "Detecting startup info"))
  (let [system-info (find-python-info options)
        _ (when-not system-info
            (if python-executable
              (throw (Exception. (format
                                  "Failed to find value python executable.  Tried \"%s\""
                                  python-executable)))
              (throw (Exception. (format
                                  "Failed to find value python executable.  Tried %s"
                                  default-python-executables)))))
        python-home (find-python-home system-info options)
        platform (:platform system-info)
        java-lib-path (java-library-path-addendum python-home)
        [ver-maj ver-med _ver-min] (:version system-info)
        lib-version                (format "%s.%s" ver-maj ver-med)
        libname                    (or library-path
                                       (when (seq lib-version)
                                         (str "python" lib-version "m")))
        libnames                   (concat [libname]
                                           ;;Make sure we try without the 'm' suffix
                                           (when lib-version
                                             [(str "python" lib-version)])
                                           ;; The official python dll
                                           ;; does not have a dot in
                                           ;; its name.
                                           (when (= platform "win32")
                                             [(str "python" ver-maj ver-med)]))]
    (merge
     system-info
     {:python-home                python-home
      :lib-version                lib-version
      :libname                    libname
      :libnames                   libnames}
     (when java-lib-path
       {:java-library-path-addendum java-lib-path}))))
