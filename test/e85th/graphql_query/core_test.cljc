(ns e85th.graphql-query.core-test
  (:require [e85th.graphql-query.core :as gq]
            #?@(:cljs [[cljs.test :refer-macros [is are deftest testing]]
                       [orchestra-cljs.spec.test :as st]]
                :clj  [[clojure.test :refer :all]
                       [clojure.java.io :as io]
                       [orchestra.spec.test :as st]])))

(st/instrument)

(deftest tokenize-query-test
  (is (=  [""] (#'gq/tokenize-query "")))
  (is (=  ["{}"] (#'gq/tokenize-query "{}")))
  (is (=  ["{me {id name}"] (#'gq/tokenize-query "{me {id name}")))
  (is (=  ["{user(id: "
           {:key :id :arg? true}
           ") {name email}}"]
          (#'gq/tokenize-query "{user(id: :id) {name email}}")))
  (is (=  ["mutation {authenticate(email: "
           {:key :email :arg? true}
           " password: "
           {:key :password :arg? true}
           ") {token name}}"]
          (#'gq/tokenize-query "mutation {authenticate(email: :email password: :password) {token name}}")))
  (is (=  ["{user(id: 123, name: "
           {:key :name :arg? true}
           ") {name email}}"]
          (#'gq/tokenize-query "{user(id: 123, name: :name) {name email}}")))
  (is (=  ["{user(id: 123, name: "
           {:key :user/name :arg? true}
           ") {name email}}"]
          (#'gq/tokenize-query "{user(id: 123, name: :user/name) {name email}}")))
  (is (=  ["{user(id: 123, name: "
           {:key :name :arg? true}
           ") {name "
           {:key :field :arg? false}
           "}}"]
          (#'gq/tokenize-query "{user(id: 123, name: :name) {name :field}}")))
  (is (=  ["{user(id: 123, name: "
           {:key :user/name :arg? true}
           ") {name "
           {:key :some/field :arg? false}
           "}}"]
          (#'gq/tokenize-query "{user(id: 123, name: :user/name) {name :some/field}}")))
  (is (=  ["{user(id: "
           {:key :id :arg? true}
           ") {name address(type: "
           {:key :type :arg? true}
           ") {city "
           {:key :field :arg? false}
           " state}}}"]
          (#'gq/tokenize-query "{user(id: :id) {name address(type: :type) {city :field state}}}"))))

(deftest hydrate-test
  (is (=  ""
          (gq/hydrate "" {})))

  (is (=  "{}"
          (gq/hydrate "{}" {})))

  (is (=  "{me {id name}"
          (gq/hydrate "{me {id name}" {})))

  (is (=  "{user(id: 100) {name email}}"
          (gq/hydrate "{user(id: :id) {name email}}" {:id 100})))

  (is (=  "{user(email: \"foo@bar.com\") {name email}}"
          (gq/hydrate "{user(email: :email) {name email}}" {:email "foo@bar.com"})))

  (is (=  "mutation {authenticate(email: \"foo@bar.com\" password: \"123\") {token name}}"
          (gq/hydrate "mutation {authenticate(email: :email password: :password) {token name}}"
                      {:email "foo@bar.com" :password "123"})))

  (is (=  "{user(id: 123, name: \"mary\") {name email}}"
          (gq/hydrate "{user(id: 123, name: :name) {name email}}" {:name "mary"})))

  (is (=  "{user(id: 123, name: \"mary\") {name email}}"
          (gq/hydrate "{user(id: 123, name: :name) {name :field}}" {:name "mary" :field "email"})))

  (is (=  "{user(id: 100) {name address(type: \"home\") {street city state}}}"
          (gq/hydrate "{user(id: :id) {name address(type: :type) {street :field state}}}" {:id 100 :type "home" :field "city"})))

  (is (=  "{user(id: 100) {name address(type: \"home\") {street city state}}}"
          (gq/hydrate "{user(id: :user/id) {name address(type: :address/type) {street :some/field state}}}" {:user/id 100 :address/type "home" :some/field "city"}))))

(deftest parse-var-defs*-test
  (testing "parse query with name"
    (is (= [{:name 'foo :query "query foo { viewer {name email} }"}]
           (#'gq/parse-var-defs* "query foo { viewer {name email} }"))))

  (testing "fail when no identifier for query."
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo
                    :cljs js/Error)
                 (#'gq/parse-var-defs* "query { viewer {name email} }")))))

(deftest squeeze-whitespace-test
  (is (= "" (#'gq/squeeze-whitespace " ")))
  (is (= "" (#'gq/squeeze-whitespace "  ")))
  (is (= "" (#'gq/squeeze-whitespace "  \n  ")))
  (is (= "{" (#'gq/squeeze-whitespace "  {  ")))
  (is (= "(" (#'gq/squeeze-whitespace "  (  ")))

  (is (= "foo{" (#'gq/squeeze-whitespace " foo {  ")))
  (is (= "{foo(a: 23){x y z}}" (#'gq/squeeze-whitespace " { foo (a: 23)    { x y z    }   }  "))))


(deftest substitute-snips-test
  (is (= "hi how are mary you mary?"
       (#'gq/substitute-snips {"a" "hi how"
                               "b" "are ${name}"
                               "name" "mary"}
                              "${a} ${b} you ${name}?"))))


#?(:clj
   (do
     (deftest parse-var-defs-test
       (let [var-defs (->> (#'gq/parse-var-defs (io/as-file "test/data/queries.graphql"))
                           (remove :snip))
             parse-name #(or (:name %) (:name- %))]
         (is (= '[query-repositories authenticate megaQuery createRepo alertsSubscription createRepoVarSub query-repositories-var-sub]
                (map parse-name var-defs)))))



     (deftest defqueries-test
       ;; defqueries below defines the vars, so remove them if they already exist
       (doseq [sym '[query-repositories authenticate mega-query create-repo alerts-subscription]]
         (ns-unmap *ns* sym))


       (gq/defqueries "data/queries.graphql")

       (is (= "{repositories(first: 100, orderBy:{field: STARGAZERS, direction: DESC}){totalCount nodes{defaultBranchRef{target{...commit}}}}}fragment commit on Commit{history(first: 100){edges{node{committedDate}}}}"
              (query-repositories)))

       (is (= "mutation{authenticate(email: \"foo@bar.com\"password: \"abc\"){user{id name}token}}"
              (authenticate {:email "foo@bar.com" :password "abc"})))

       (is (= "query megaQuery($orgName: String!){viewer{name email}repositories(orgName: $orgName){name url createdAt}}"
              (mega-query)))

       (is (= "mutation createRepo{createRepo(input:{name: \"foo\"}){name url createdAt}}"
              (create-repo {:name "foo"})))

       (is (= "subscription alertsSubscription{alerts(repo: \"foo\"){message generatedAt}}"
              (alerts-subscription {:name "foo"})))

       (is (= "mutation createRepoVarSub{createRepo(input:{name: \"foo\"}){name url createdAt}}"
              (create-repo-var-sub {:name "foo"})))

       (is (= "{repositories(first: 100, orderBy:{field: STARGAZERS, direction: DESC}){totalCount nodes{defaultBranchRef{target{...commit}}}}}fragment commit on Commit{history(first: 100){edges{node{committedDate}}}}"
              (query-repositories-var-sub))))))
