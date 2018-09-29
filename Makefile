.PHONY: clean test

test:	clean
	clj -A:test-clj:dev
	clj -A:test-cljs:dev

clean:
	rm -rf target
	rm -rf cljs-test-runner-out
