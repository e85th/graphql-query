(defproject e85th/graphql-query "0.1.0-alpha1"
  :description "Clojure(Script) graphql query."
  :url "http://github.com/e85th/graphql-query"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.9.0-beta3"   :scope "provided"]
                 [org.clojure/clojurescript "1.9.946" :scope "provided"]]
  :plugins [[lein-doo "0.1.7"]]
  :profiles {:dev {:dependencies [[orchestra "2017.08.13"]]}}
  :clean-targets ^{:protect false} ["resources" "target"]
  :aliases {"test" ["do" "test" ["doo" "once" "phantom" "test"]]}
  :cljsbuild {:builds [{:id           "test"
                        :source-paths ["src" "test"]
                        :compiler     {:output-to     "resources/test/js/unit-test.js"
                                       :process-shim  false
                                       :main          e85th.graphql-query.runner
                                       :optimizations :none
                                       :pretty-print  true
                                       :output-dir    "resources/test/js/gen/out"}}]}
  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org/repo"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]])
