# Embedding Clojure In Python


The initial development push for `libpython-clj` was simply to embed Python in
Clojure allowing Clojure developers to use Python modules simply transparently.
This approach relied on `libpython-clj` being able to find the Python shared library
and having some capability to setup various Python system variables before loading
any modules.  While this works great most of the time there are several reasons for
which this approach is less than ideal:


1.  In some cases a mainly Python team wants to use some Clojure for a small part of
    their work.  Telling them to host Python from Clojure is for them a potentially
	very disruptive change.
2.  The python ecosystem is moving away from the shared library and towards
    compiling a statically linked executable.  This can never work with
    libpython-clj's default pathway.
3.  Embedded Python cannot use all of Python functionality available due to the fact
    that the host process isn't `python`.  Specifically the multithreading module
    relies on forking the host process and thus produces a hang if the JVM is the
    main underlying process.
4.  Replicating the exact python environment is error prone especially when Python
    environment managers such as `pyenv` and `Conda` are taken into account.


Due to the above reasons there is a solid argument for, if possible, embedding
Clojure into Python allowing the Python executable to be the host process.


## Enter: cljbridge


Python already had a nascent system for embedding Java in Python - the
[javabridge module](https://pypi.org/project/javabridge/). 

We went a step further and provide `cljbridge` python module.

In order to compile `javabridge`
a JDK is required and **not just the JRE**.  [tristanstraub](https://github.com/tristanstraub/)
had found a way to use this in order to work with [Blender](https://github.com/tristanstraub/blender-clj/).
We took a bit more time and worked out ways to smooth out these interactions
and make sure they were supported throughout the system.


## From the Python REPL


The next step involves starting a python repl.

This requires a python library `cljbridge`,
which can be installed via

```
export JAVA_HOME=<--YOUR JAVA HOME-->
python3 -m pip install cljbridge
```

This will install and eventually compile `javabridge` as well.

If the installation cannot find 'jni.h' then most likely you have the Java runtime
(JRE) installed as opposed to the Java development kit (JDK).

So we start by importing
that script:


```python
Python 3.8.5 (default, Jan 27 2021, 15:41:15)
[GCC 9.3.0] on linux
Type "help", "copyright", "credits" or "license" for more information.
>>> from clojurebridge import cljbridge
>>> test_var=10
>>> cljbridge.init_jvm(start_repl=True)
Mar 11, 2021 9:08:47 AM clojure.tools.logging$eval3186$fn__3189 invoke
INFO: nREPL server started on port 40241 on host localhost - nrepl://localhost:40241
```

At this point we do not get control back; we have released the GIL and java
is blocking this thread to allow the Clojure REPL systems access to the GIL.  We have
two important libraries for clojure loaded, nrepl and cider which allow a rich,
interactive development experience so let's now connect to that port with our favorite
Clojure editor - emacs of course ;-).

If you want to specify arbitrary arguments for the JVM to be started by Python,
you can use the environment variable `JDK_JAVA_OPTIONS` to do so. It will be picked up by 
the JVM when starting.
```
```

## From the Clojure REPL


From emacs, I run the command 'cider-connect' which allows me to specify a host
and port to connect to.  Once connected, I get a minimal repl environment:


```clojure
;; M-x cider-connect ...localhost...40241
;; I am not sure why but to initialize the user namespace I have to eval ns user

user> (eval '(ns user))
nil
user> (require '[libpython-clj2.python :as py])
nil
;; Python has been initialized and libpython-clj can detect this
user> (py/initialize!)
:already-initialized
user> ;;We can share data via the main module
user> (def main-mod (py/add-module "__main__"))
#'user/main-mod
user> (def mod-dict (py/module-dict main-mod))
#'user/mod-dict
user> (keys mod-dict)
("__name__"
 "__doc__"
 "__package__"
 "__loader__"
 "__spec__"
 "__annotations__"
 "__builtins__"
 "cljbridge"
 "test_var")
user> (get mod-dict "test_var")
10
user> (.put mod-dict "clj_fn" (fn [& args] (println "Printing from Clojure: " (vec args))))
nil
user> ;;Now if we stop the repl server we can access our python environment again
user> (require '[libpython-clj2.embedded :as embedded])
nil
user> (embedded/stop-repl!)
```

## And Back to Python!!

Shutting down the repl always gives us an exception; something perhaps to work on.
But the important thing is that we can access variables and data that we set
in the main module -
```python
>>> Exception in thread "nREPL-session-d684061e-f21c-4265-a9a2-828b99dcaf42" java.net.SocketException: Socket closed
	at java.net.SocketOutputStream.socketWrite(SocketOutputStream.java:118)
	at java.net.SocketOutputStream.write(SocketOutputStream.java:155)
	at java.io.BufferedOutputStream.flushBuffer(BufferedOutputStream.java:82)
	at java.io.BufferedOutputStream.flush(BufferedOutputStream.java:140)
	at nrepl.transport$bencode$fn__7714.invoke(transport.clj:121)
	at nrepl.transport.FnTransport.send(transport.clj:28)
	at nrepl.middleware.print$send_streamed.invokeStatic(print.clj:136)
	at nrepl.middleware.print$send_streamed.invoke(print.clj:122)
	at nrepl.middleware.print$printing_transport$reify__8149.send(print.clj:173)
	at cider.nrepl.middleware.track_state$make_transport$reify__17923.send(track_state.clj:228)
	at nrepl.middleware.caught$caught_transport$reify__8184.send(caught.clj:58)
	at nrepl.middleware.interruptible_eval$evaluate$fn__8250.invoke(interruptible_eval.clj:132)
	at clojure.main$repl$fn__9121.invoke(main.clj:460)
	at clojure.main$repl.invokeStatic(main.clj:458)
	at clojure.main$repl.doInvoke(main.clj:368)
	at clojure.lang.RestFn.invoke(RestFn.java:1523)
	at nrepl.middleware.interruptible_eval$evaluate.invokeStatic(interruptible_eval.clj:84)
	at nrepl.middleware.interruptible_eval$evaluate.invoke(interruptible_eval.clj:56)
	at nrepl.middleware.interruptible_eval$interruptible_eval$fn__8258$fn__8262.invoke(interruptible_eval.clj:152)
	at clojure.lang.AFn.run(AFn.java:22)
	at nrepl.middleware.session$session_exec$main_loop__8326$fn__8330.invoke(session.clj:202)
	at nrepl.middleware.session$session_exec$main_loop__8326.invoke(session.clj:201)
	at clojure.lang.AFn.run(AFn.java:22)
	at java.lang.Thread.run(Thread.java:748)

>>> # So let's call our new clojure fn
>>> clj_fn(1, 2, 3, 4, "Embedded Clojure FTW!!")
Printing from Clojure:  [1 2 3 4 Embedded Clojure FTW!!]
>>>
```
## Loading and running a Clojure file in embedded mode

We can runs as well a .clj file in embedded mode. 
The following does this without an interactive pytho shell, it just runs the provided clj file with
`clojure.core/load-file`

```bash
python3 -c 'import cljbridge;cljbridge.load_clojure_file(clj_file="my-file.clj")'
```


## Are You Not Entertained???


So there you have it, embedding a Clojure repl in a Python process and passing data
in between these two systems.  This sidesteps a *ton* of issues with embedding Python
and provides another interesting set of possibilities, essentially extending existing
Python systems with some of the greatest tech the JVM has to offer :-).


* [javabridge github](https://github.com/LeeKamentsky/python-javabridge)
* [libpython-clj cljbridge.py](https://github.com/clj-python/libpython-clj/blob/94c72ca0ac94b210a9b126805cd4112024ad0b96/cljbridge.py)
* [libpython-clj embedded docs](https://clj-python.github.io/libpython-clj/libpython-clj2.embedded.html)
* [blender-clj - the inspiration](https://github.com/tristanstraub/blender-clj/)
