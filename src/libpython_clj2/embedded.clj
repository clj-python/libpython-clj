(ns libpython-clj2.embedded
  "Tools for embedding clojure into a python host process.
  See jbridge.py for python details.  This namespace relies on
  the classpath having nrepl and cider-nrepl on it.  For example:

```console
clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version \"0.8.3\"} cider/cider-nrepl {:mvn/version \"0.25.5\"}}}' ...
```"
  (:require [libpython-clj2.python.ffi :as py-ffi]
            [clojure.tools.logging :as log]))


(defn initialize-embedded!
  "Initialize python when this library is being called *from* a python program.  In
  that case, unless libpath is explicitly provided the system will look for the
  python symbols in the current executable."
  ([] (py-ffi/set-library! nil))
  ([libpath] (py-ffi/set-library! libpath)))


(defonce ^:private repl-server* (atom nil))


(defn stop-embedded-repl!
  "If an existing repl has been started, stop it.  This returns control to the
  python process."
  []
  (swap! repl-server*
         (fn [server]
           (when server
             (let [close-fn (requiring-resolve 'nrepl.server/stop-server)]
               (try
                 (locking #'repl-server*
                   (close-fn server)
                   (.notifyAll ^Object #'repl-server*))
                    nil
                    (catch Throwable e
                      (log/errorf e "Failed to stop nrepl server!")
                      nil)))))))


(defn start-embedded-repl!
  "This is called to start a clojure repl from python.  Do not ever call this if
  not from a python thread as it starts with releasing the GIL.  This function
  does not return until another thread calls stop-embedded-repl!.

  Options are the same as the command line options found in nrepl.cmdline"
  ([options]
   (stop-embedded-repl!)
   (let [tstate (when (== 1 (long (py-ffi/PyGILState_Check)))
                  (py-ffi/PyEval_SaveThread))]
     (try
       (let [server-opts-fn (requiring-resolve 'nrepl.cmdline/server-opts)
             options (server-opts-fn
                      (merge {:middleware '[cider.nrepl/cider-middleware]}
                             options))
             server-fn (requiring-resolve 'nrepl.cmdline/start-server)
             ack-fn (requiring-resolve 'nrepl.cmdline/ack-server)
             message-fn (requiring-resolve 'nrepl.cmdline/server-started-message)
             portfile-fn (requiring-resolve 'nrepl.cmdline/save-port-file)
             server (server-fn options)
             _ (reset! repl-server* server)]
         (ack-fn server options)
         (portfile-fn server options)
         (log/info (message-fn server options))
         (locking #'repl-server*
           (.wait ^Object #'repl-server*)))
       (finally
         (when tstate
           (py-ffi/PyEval_RestoreThread tstate))))))
  ([] (start-embedded-repl! nil)))
