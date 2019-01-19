# graphql-query

A Clojure library designed to facilitate working with graphql operations.
If you are familiar with Hugsql or Yesql, this works in a similar manner.
Queries are strings that contain clojure keywords.  Ideally, the GraphQL
server handles variables and all your operations are parameterized.

## Rationale
* GraphQL queries are strings and the syntax makes it difficult to convert
to Clojure data structures and vice versa.

* The queries are generally static and likely don't need the complexity with
dynamic graphql query generation.

* Can test the queries easily in GraphiQL without going through a translation layer.

## Usage
Given a graphql file `queries.graphql` in the `resources` directory:

```graphql
query queryRepositiories {
  repositories {
    name
    url
  }
}

subscription alertsSubscription {
  alerts(repo: :name) {
    message
    generatedAt
  }
}

# {:name authenticate :doc "Auth a user"}
mutation {
  authenticate(email: :email password: :password) {
    user {
      id
      name
    }
    token
  }
}
```

and the following Clojure code

```clj
  (ns example
    (:require [e85th.graphql-query.core :as gq]))

   ;; define queries via vars similar to Hugsql
   (gq/defqueries "queries.graphql")
```



The result of `defqueries` is there are 3 new functions defined in that namespace:
 * `query-repositiories`
 * `authenticate`
 * `alerts-subscription`


They can be called as:

```clj
(example/query-repositiories) ; convenience 0 arity when param replacement is not necessary`
(example/authenticate {:user "foo@bar.com" :password "abc"})
```

## Other examples:
```clj
  (ns example
    (:require [e85th.graphql-query.core :as gq]))

   (= "{user(id: 100) {name email}}"
       (gq/hydrate "{user(id: :id) {name email}}" {:id 100}))

   ;; can use namespaced keywords
   (= "{user(email: \"foo@bar.com\") {name email}}"
       (gq/hydrate "{user(email: :user/email) {name email}}" {:user/email "foo@bar.com"}))

   ;;-----------------------------------------------------------------------------
   ;; Options:
   ;;
   ;; squeeze? produces queries which have whitespace removed
   ;; lisp-case? creates vars from named queries in lisp case
   ;;            eg. 'query getRepo {}' will yield (defn get-repo [params] ...)
   ;;
   ;; Below is the same as using the 1 arity version.
   ;;-----------------------------------------------------------------------------
   (gq/defqueries "queries.graphql" {:squeeze? true :lisp-case? true})
```


## Syntax

`#` is the comment character as in graphql

`# {`  -- indicates start of edn for the rest of the line. If the rest of the line is valid edn, `:name` or `:name-` and `:doc` keys are processed.

`:name-` will define a var that is private.


Any line beginning with `query`, `mutation`, or `subscription` triggers the definition of a new `defn` unless
if there is already a `# {:` above it.

## Variable Substitution
First define a `snip`.
```
# {:snip repo-info}
  name
  url
  createdAt
```

Later use the snip.
```
mutation createRepo {
  createRepo(input: {name: :name}) {
    ${repo-info}
  }
}
```

which will be expanded to
```
mutation createRepo {
  createRepo(input: {name: :name}) {
    name
    url
    createdAt
  }
}
```


## Gotchas
```graphql
# NB. This will throw an exception since there is no name after query and no #{:name } above it.
query {
  viewer {
    name
    email
  }
}
```

Please see the tests and `test/data/queries.graphql` for more examples.


## Alternatives
[graphql-builder](https://github.com/retro/graphql-builder)
More functionality, actually understands graphql syntax.

[venia](https://github.com/Vincit/venia)
Requires translation of queries to clojure data structures. Great for
when a truly dynamic query is required.


## Future
* Make definitions be strings instead of functions by default?

## License

Copyright © 2017 E85th Contributors

Distributed under the Apache License 2.0.
