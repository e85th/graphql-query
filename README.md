# graphql-query

A Clojure library designed to facilitate working with graphql queries.
If you are familiar with Hugsql or Yesql, this works in a similar manner.
Queries are strings that contain clojure keywords.

## Usage

```clj
  (ns example
    (:require [e85th.graphql-query.core :as gq]))

   (= "{user(id: 100) {name email}}"
       (gq/hydrate "{user(id: :id) {name email}}" {:id 100}))

   ;; can use namespaced keywords
   (= "{user(email: \"foo@bar.com\") {name email}}"
       (gq/hydrate "{user(email: :user/email) {name email}}" {:user/email "foo@bar.com"}))

   ;; define queries similar to Hugsql
   (gq/defqueries "queries.graphql") ;; if queries.graphql is under the resources dir

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


The file below yields three functions in the namespace that `defqueries` is defined in.
 * `query-repositiories`  is public
 * `authenticate`         is private `:name-`
 * `alerts-subscription`  is public

They can be called as:

```clj
(query-repositiories) ; convenience 0 arity when param replacement is not necessary`
(authenticate {:user "foo@bar.com" :password "abc"})
```


## Syntax

`#` is the comment character as in graphql

`# {`  -- indicates start of edn for the rest of the line. If the rest of the line is valid edn, `:name` or `:name-` and `:doc` keys are processed.

Any line beginning with `query`, `mutation`, or `subscription` triggers the definition of a new `defn` unless
if there is already a `# {:` above it.


queries.graphql
```graphql
# {:name query-repositories :doc "Get repositoriy info."}
{
  repositories {
    name
    url
  }
}

# {:name- authenticate :doc "Auth a user"}
mutation {
  authenticate(email: :email password: :password) {
    user {
      id
      name
    }
    token
  }
}

subscription alertsSubscription {
  alerts(repo: :name) {
    message
    generatedAt
  }
}


# NB. This will throw an exception since there is no name after query.
query {
  viewer {
    name
    email
  }
}
```

Please see the tests and `test/data/queries.graphql` for more examples.

## License

Copyright © 2017 E85th Contributors

Distributed under the Apache License 2.0.
