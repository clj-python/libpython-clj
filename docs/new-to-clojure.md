# So Many Parenthesis!


## About Clojure


LISP stands for List Processing and it was originally designed by John McCarthy
around 1958.  It was the first language with a garbage collector making it the first
truly high level language assuming you don't consider Fortran a high level language.
Here is Dr. McCarthy's [seminal paper](http://www-formal.stanford.edu/jmc/recursive.pdf)
and for a much better intro than I can give please see
[here](http://www.paulgraham.com/rootsoflisp.html).


Time passed and along came a man named Rich Hickey.  Making a long story short, Rich was
working in a variety of languages such as C++, Java, and C# when he did a project in
Common Lisp and was hooked.  There are many YouTube videos and documents that Rich has
produced but [simple made easy](https://www.infoq.com/presentations/Simple-Made-Easy/)
is one I found very compelling.  If you enjoy that video, don't stop there; Rich has
many interesting, profound, and sometimes provocative things to add to the conversation.
For more about his reasoning behind Clojure, please check out his 
[rationale](https://clojure.org/about/rationale).


To address the parenthesis, we need to talk about
[homoiconicity](https://en.wikipedia.org/wiki/Homoiconicity).  LISPs are part of a
subset of languages that build themselves out of their own data structures so that when
you type symbols into a file or repl, you get back a data structure of the language
itself.  This means that several parts of the programmers toolbox can be simpler and you
can load a file as data, transform it, and then execute some portion of it all without
leaving the language.  This isn't something you will really need to understand today,
but the point is that the look and structure of the language is a sweet spot to make it
more of middle ground between what a human and a machine can understand.


The fallout from having a language that is both a language and a data structure is that
you can extend the language without needing to change the compiler.  For example, the
very first standardized 'object oriented programming' system was
[CLOS](https://en.wikipedia.org/wiki/Common_Lisp_Object_System), or Common Lisp Object
System.  This was implemented on top of Common Lisp with no updates to the compiler
whatsoever.  In Clojure, we have been able to add things like an 
[async library](https://github.com/clojure/core.async) or 
[software transactional memory](https://clojure.org/reference/refs) without changing the 
compiler itself because we can extend or change the language quite substantially at compile 
time.


Clojure is a deeply functional language with pragmatic pathways built for mutation.  All
of the basic data structures of Clojure are immutable.  Learning to program in a
functional manner will mean learning things like `map` and `reduce` and basically
re-wiring how you think about problems.  I believe it is this re-wiring that is most
beneficial in the long term regardless of whether you become some functional programming
God or just dabble for a while.


Many systems, regardless of language, are designed in a functional manner because
properties like [idempotency](https://en.wikipedia.org/wiki/Idempotence) and
[referential transparency](https://en.wikipedia.org/wiki/Referential_transparency) make
it easier to reason about code that you didn't write.  That being said, Clojure doesn't
force you to write functional code all the time as it is mainly a pragmatic language.
You can easily write mutable code if you like.


For the web, Clojure has a [version](https://clojurescript.org/) that compiles to JavaScript
so that you can write one language both server and front end side.  Many Clojure projects
are just one repository and one artifact that when run produces both the server and
client side of the equation.  This is truly one of Clojure's greatest strengths and one
that the Clojure community has strongly embraced.


No talk about Clojure would be complete without giving major credit to its excellent
REPL support.  One important aspect of the Clojure REPL is that you can see all of
complex nested datastructures easily without needing to write `toString` or `__str__`
methods.  Because of this visibility advantage a common way to use Clojure is to model
your problem as a transformation from datastructure to datastructure, testing each stage
of this transformation in the repl and just letting the REPL printing show you the next
move.  Programming with data is often just easier than programming with objects and
debugging data transformations is far, far easier than debugging a complex object graph.


## Learning Clojure


There are in fact many resources to learn Clojure and here are some the community 
recommends:


### Books/Courses


1.  [Clojure for the Brave and True](https://www.braveclojure.com/clojure-for-the-brave-and-true/)
1.  [Clojure for Data Science](https://www.amazon.it/dp/B00YSILGWG/ref=dp-kindle-redirect?_encoding=UTF8&btkr=1)
1.  [Functional Programming in Scala and Clojure](https://www.amazon.it/dp/B00HUEG8KK/ref=dp-kindle-redirect?_encoding=UTF8&btkr=1)
1.  [Practicalli](https://practicalli.github.io/clojure/)


Practically has a page devoted purely to resources on learning Clojure at whatever level
you are so if these starts do not speak to you please review the their 
[books page](https://practicalli.github.io/clojure/reference/books.html).


### Learn by Writing Code


1. [clojinc](https://github.com/lspector/clojinc)
1. [Clojure Koans](https://github.com/functional-koans/clojure-koans)


### IDEs/Editors

There are many more IDEs available than listed here; these ones are just very popular:


1.  [Calva](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva)
1.  [Cursive](https://cursive-ide.com/)
1.  [Atom](https://medium.com/@jacekschae/slick-clojure-editor-setup-with-atom-a3c1b528b722)
1.  [emacs + cider](https://cider.mx/)
1.  [vim + fireplace](https://www.vim.org/scripts/script.php?script_id=4978)


One thing to be sure of, regardless of IDE, is to use some form of 
[structural editing](https://shaunlebron.github.io/parinfer/).  All the better IDEs 
have it; all the IDEs listed here have it, and I personally really struggle without it.
When I have a form of [structure editing](https://wikemacs.org/wiki/Paredit-mode), however, 
I can move code around much faster than I can in Java, Python, or C++. This is another 
benefit of the homoiconicity we spoke earlier in that we can transform the program easily 
because it is just a data structure and this includes editor level transformations and
analysis.


## Off We Go!


Clojure is an amazing language.  It is really rewarding on a personal level because it
is tailored towards extremely high individual productivity.  But this power comes with 
some caveats and one of those is that learning Clojure takes time and patience.  The 
community is here to support you, however, so check us out:


* [clojurians slack](https://clojurians.slack.com)
* [Clojure on Zulip](https://clojurians.zulipchat.com/)
* [r/Clojure reddit](https://www.reddit.com/r/Clojure/)
* [r/Clojurescript reddit](https://www.reddit.com/r/Clojurescript/)
* [Clojureverse](https://clojureverse.org/)
