(ns e85th.graphql-query.core-test
  (:require [e85th.graphql-query.core :as gq]
            #?@(:cljs [[cljs.test :refer-macros [is are deftest testing]]
                       [orchestra-cljs.spec.test :as st]]
                :clj  [[clojure.test :refer :all]
                       [orchestra.spec.test :as st]])))

(st/instrument)

(defmacro expect
  [x y]
  `(is (= ~x ~y)))

(deftest tokenize-query-test
  (expect [""] (#'gq/tokenize-query ""))
  (expect ["{}"] (#'gq/tokenize-query "{}"))
  (expect ["{me {id name}"] (#'gq/tokenize-query "{me {id name}"))
  (expect ["{user(id: "
           {:key :id :arg? true}
           ") {name email}}"]
          (#'gq/tokenize-query "{user(id: :id) {name email}}"))
  (expect ["mutation {authenticate(email: "
           {:key :email :arg? true}
           " password: "
           {:key :password :arg? true}
           ") {token name}}"]
          (#'gq/tokenize-query "mutation {authenticate(email: :email password: :password) {token name}}"))
  (expect ["{user(id: 123, name: "
           {:key :name :arg? true}
           ") {name email}}"]
          (#'gq/tokenize-query "{user(id: 123, name: :name) {name email}}"))
  (expect ["{user(id: 123, name: "
           {:key :user/name :arg? true}
           ") {name email}}"]
          (#'gq/tokenize-query "{user(id: 123, name: :user/name) {name email}}"))
  (expect ["{user(id: 123, name: "
           {:key :name :arg? true}
           ") {name "
           {:key :field :arg? false}
           "}}"]
          (#'gq/tokenize-query "{user(id: 123, name: :name) {name :field}}"))
  (expect ["{user(id: 123, name: "
           {:key :user/name :arg? true}
           ") {name "
           {:key :some/field :arg? false}
           "}}"]
          (#'gq/tokenize-query "{user(id: 123, name: :user/name) {name :some/field}}"))
  (expect ["{user(id: "
           {:key :id :arg? true}
           ") {name address(type: "
           {:key :type :arg? true}
           ") {city "
           {:key :field :arg? false}
           " state}}}"]
          (#'gq/tokenize-query "{user(id: :id) {name address(type: :type) {city :field state}}}")))

(deftest hydrate-test
  (expect ""
          (gq/hydrate "" {}))

  (expect "{}"
          (gq/hydrate "{}" {}))

  (expect "{me {id name}"
          (gq/hydrate "{me {id name}" {}))

  (expect "{user(id: 100) {name email}}"
          (gq/hydrate "{user(id: :id) {name email}}" {:id 100}))

  (expect "{user(email: \"foo@bar.com\") {name email}}"
          (gq/hydrate "{user(email: :email) {name email}}" {:email "foo@bar.com"}))

  (expect "mutation {authenticate(email: \"foo@bar.com\" password: \"123\") {token name}}"
          (gq/hydrate "mutation {authenticate(email: :email password: :password) {token name}}"
                      {:email "foo@bar.com" :password "123"}))

  (expect"{user(id: 123, name: \"mary\") {name email}}"
         (gq/hydrate "{user(id: 123, name: :name) {name email}}" {:name "mary"}))

  (expect "{user(id: 123, name: \"mary\") {name email}}"
          (gq/hydrate "{user(id: 123, name: :name) {name :field}}" {:name "mary" :field "email"}))

  (expect"{user(id: 100) {name address(type: \"home\") {street city state}}}"
         (gq/hydrate "{user(id: :id) {name address(type: :type) {street :field state}}}" {:id 100 :type "home" :field "city"}))

  (expect"{user(id: 100) {name address(type: \"home\") {street city state}}}"
         (gq/hydrate "{user(id: :user/id) {name address(type: :address/type) {street :some/field state}}}" {:user/id 100 :address/type "home" :some/field "city"})))

#?(:clj
   (deftest defqueries-test
     (gq/defqueries "data/queries.graphql")))
