(ns libpython-clj2.jdk-ffi-loader
  (:require [ham-fisted.process :as hamf-proc])
  (:import [java.lang.foreign Arena FunctionDescriptor Linker MemorySegment SymbolLookup ValueLayout Linker$Option]
           [java.nio.file Path Paths]))

;; Unix dlopen flags
(def ^:private rtld-lazy 0x00001)
(def ^:private linux-rtld-global 0x0100)
(def ^:private macos-rtld-global 0x00008)



(defn load-library-globally
  "Loads a shared library globally (RTLD_GLOBAL on Unix) using Java's FFM API.
   Returns a SymbolLookup tied to the library path."
  ^SymbolLookup [library-path ^Arena arena]
  (let [os (.. System (getProperty "os.name") (toLowerCase))
        library-path (Paths/get library-path (into-array String []))]
    (when-not (.contains os "win")
      ;; Linux / macOS require manual dlopen with RTLD_GLOBAL
      (let [global-flag (if (.contains os "mac")
                          macos-rtld-global
                          linux-rtld-global)
            flags (bit-or rtld-lazy global-flag)
            linker (Linker/nativeLinker)
            _ (println "0")
            ;; Locate 'dlopen' in default C runtime lookup
            libdl (.defaultLookup linker)
            #_(try
              (SymbolLookup/libraryLookup "libdl.so.2" (Arena/global))
              (catch Exception _
                (SymbolLookup/libraryLookup "libc.so.6" (Arena/global))))
            dlopen-addr (.. libdl (find "dlopen") (get))
            ;; void* dlopen(const char* filename, int flags)
            sig (FunctionDescriptor/of
                 ValueLayout/ADDRESS
                 (into-array ValueLayout [ValueLayout/ADDRESS ValueLayout/JAVA_INT]))
            dlopen-handle (.downcallHandle linker dlopen-addr sig (into-array Linker$Option []))]
        ;; Allocate C-string in off-heap memory and invoke dlopen
        (with-open [temp-arena (Arena/ofConfined)]
          (let [path-str (.. library-path toAbsolutePath toString)
                path-segment (.allocateFrom temp-arena path-str)
                handle ^MemorySegment (.invokeWithArguments dlopen-handle (object-array [path-segment (int flags)]))]

            (when (.equals handle MemorySegment/NULL)
              (throw (UnsatisfiedLinkError.
                      (str "Failed to load library globally via dlopen: " library-path))))))

        ;; Return FFM SymbolLookup backed by Java's native wrapper)
        )
      :ok)))

(comment
  (do
    (def py-script ["python3" "-c" "import sysconfig; print(sysconfig.get_config_var('LIBDIR'), sysconfig.get_config_var('INSTSONAME'))"])
    (def libpath (->> (-> (hamf-proc/sh py-script)
                          :out
                          (str)
                          (.trim)
                          (.split " ")
                          (vec))
                      (interpose "/")
                      (apply str)))
    (def sym-lookup (load-library-globally libpath (Arena/global)))
    (require '[libpython-clj2.python :as py])
    (py/initialize! :library-path libpath))

  (def np (py/import-module "numpy"))

  :-)
