# So Many Parenthesis!


## About Clojure


First let me tell you a bit about the history of Clojure as I think it will help with
the process of understanding how things came to be the way they are.


LISP stand for List Processing and it was originally designed by John McCarthy
around 1958.  It was the first language with a garbage collector making it the first
truly high level language assuming you don't consider Fortran a high level language.
Here is Dr. McCarthy's [seminal paper](http://www-formal.stanford.edu/jmc/recursive.pdf)
and for a must better intro than I can give please see
[here](http://www.paulgraham.com/rootsoflisp.html).


Time passed and along came a man named Rich Hickey.  Making a long story short, Rich was
working in a variety of languages such as C++, Java, and C# when he did a project in
CommonLisp and was hooked.  There are many youtube videos and documents that Rich has
produced but [simple made easy](https://www.infoq.com/presentations/Simple-Made-Easy/)
is one I found very compelling.  If you enjoy that video, don't stop there; Rich has
many interesting, profound, and sometimes provocative things to add to the conversation.


To address the parenthesis, we need to talk about
[homoiconicity](https://en.wikipedia.org/wiki/Homoiconicity).  LISP's are part of a
subset of languages that build themselves out of their own datastructures so that when
you type symbols into a file or repl, you get back a datastructure of the language
itself.  This means that several parts of the programmers toolbox can be simpler and you
can load a file as data, transform it, and then execute some portion of it all without
leaving the language.  This isn't something you will really need to understand today,
but the point is that the look and structure of the language is a tradeoff to make it
more of middle ground between what a human and a machine can understand.


Clojure is a deeply functional language with pragmatic pathways built for mutation.  All
of the basic datastructures of Clojure are immutable.  Learning to program in a
functional manner will mean learning things like `map` and `reduce` and basically
re-wiring how you think about problems.  It is this re-wiring that is most beneficial in
the long term regardless of whether you become some functional programming God or just
dabble for a while.


Many systems, regardless of language, are designed in a functional manner because
properties like [idempotency](https://en.wikipedia.org/wiki/Idempotence) and
[referential transparency](https://en.wikipedia.org/wiki/Referential_transparency) make
it easier to reason about code that you didn't write.  That being said, Clojure doesn't
force you to write functional code all the time as it is mainly a pragmatic language.
You can easily write mutable code if you like.


Finally, Clojure has a [version](https://clojurescript.org/) that compiles to JavaScript
so that you can write one language both server and frontend side.  Many Clojure projects
are just one repository and one artifact that when run produces the server and client
side of the equation.  This is truly one of Clojure's greatest strengths and one that
the Clojure community has strongly embraced.



## Learning Clojure


There are in fact many resources to learn Clojure and here are some the community 
recommends:


### Books


1.  [Clojure for the Brave and True](https://www.braveclojure.com/clojure-for-the-brave-and-true/)
1.  [Clojure for Data Science](https://www.amazon.it/dp/B00YSILGWG/ref=dp-kindle-redirect?_encoding=UTF8&btkr=1)
1.  [Functional Programming in Scala and Clojure](https://www.amazon.it/dp/B00HUEG8KK/ref=dp-kindle-redirect?_encoding=UTF8&btkr=1)


### Learn by Writing Code


If you like to learn by programming then we have seen success in the [Clojure Koans](https://github.com/functional-koans/clojure-koans).


### IDE's/Editors

There are many more than listed here but these editors and IDE's are often used:


1.  [Calva](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva)
1.  [Cursive](https://cursive-ide.com/)
1.  [emacs + cider](https://cider.mx/)
1.  [vim + fireplace](https://www.vim.org/scripts/script.php?script_id=4978)


## Off We Go!


Clojure is an amazing language.  It is really rewarding on a personal level because it
is tailored towards extremely high individual productivity.  But this power comes with 
some caveats and one of those is that learning Clojure takes time and patience.  The 
community is here to support you, however, so check us out:


* [clojurians slack](https://clojurians.slack.com]
* [Clojure on Zulip](https://clojurians.zulipchat.com/)
* [r/Clojure reddit](https://www.reddit.com/r/Clojure/)
* [r/Clojurescript reddit](https://www.reddit.com/r/Clojurescript/)
* [Clojureverse](https://clojureverse.org/)
