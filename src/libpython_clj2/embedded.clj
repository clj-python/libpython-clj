(ns libpython-clj2.embedded
  "Tools for embedding clojure into a python host process.
  See jbridge.py for python details.  This namespace relies on
  the classpath having nrepl and cider-nrepl on it.  For example:

```console
clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version \"0.8.3\"} cider/cider-nrepl {:mvn/version \"0.25.5\"}}}' ...
```"
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [nrepl.server :as server]
            [nrepl.cmdline :as cmdline]
            [clojure.tools.logging :as log]))


(defn initialize!
  "Initialize python when this library is being called *from* a python program.  In
  that case, unless libpath is explicitly provided the system will look for the
  python symbols in the current executable."
  ([] (py-ffi/set-library! nil))
  ([libpath] (py-ffi/set-library! libpath)))


(defonce ^:private repl-server* (atom nil))


(defn stop-repl!
  "If an existing repl has been started, stop it.  This returns control to the
  python process."
  []
  (swap! repl-server*
         (fn [server]
           (when server
             (try
               (locking #'repl-server*
                 (server/stop-server server)
                 (.notifyAll ^Object #'repl-server*))
               nil
               (catch Throwable e
                 (log/errorf e "Failed to stop nrepl server!")
                 nil))))))


(defn start-repl!
  "This is called to start a clojure repl from python.  Do not ever call this if
  not from a python thread as it starts with releasing the GIL.  This function
  does not return until another thread calls stop-embedded-repl!.

  If an existing repl server has been started this returns the port of the previous
  server else it returns the port of the new server.

  Options are the same as the command line options found in nrepl.cmdline."
  ([options]
   (when-not @repl-server*
     (let [tstate (when (== 1 (long (py-ffi/PyGILState_Check)))
                    (py-ffi/PyEval_SaveThread))]
       (try
         (let [options (cmdline/server-opts
                        (merge {:middleware '[cider.nrepl/cider-middleware]}
                               options))
               server (cmdline/start-server options)
               _ (reset! repl-server* server)]
           (cmdline/ack-server server options)
           (cmdline/save-port-file server options)
           (log/info (cmdline/server-started-message server options))
           (locking #'repl-server*
             (.wait ^Object #'repl-server*)))
         (finally
           (when tstate
             (py-ffi/PyEval_RestoreThread tstate))))))
   (:port @repl-server*))
  ([] (start-repl! nil)))
