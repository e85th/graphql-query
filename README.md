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
```

## License

Copyright © 2017 E85th Contributors

Distributed under the Apache License 2.0.
