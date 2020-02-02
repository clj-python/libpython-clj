build: clean
		clj -A:javac

install: build
		clj -A:install

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

