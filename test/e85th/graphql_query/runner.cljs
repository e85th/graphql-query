(ns e85th.graphql-query.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [e85th.graphql-query.core-test]))

(enable-console-print!)

(doo-tests 'e85th.graphql-query.core-test)
