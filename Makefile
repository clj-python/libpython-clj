build: clean
		clj -A:javac

clean:
		clj -A:clean

runner:
		clj -A:test

t:
		clj -A:test

clean-test: build
		clj -A:test

jar: clean
		clj -A:jar

deploy:
		clj -A:deploy

FORCE:

