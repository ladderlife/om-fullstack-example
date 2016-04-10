(defproject om-fullstack-example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/server" "src/shared"]
  :test-paths ["test/server"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.34"]
                 [org.clojure/test.check "0.9.0"]
                 [org.omcljs/om "1.0.0-alpha31"]
                 [ring/ring "1.4.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.datomic/datomic-free "0.9.5350"]
                 [figwheel-sidecar "0.5.1"]
                 [com.ladderlife/cellophane "0.2.3"]]
  :cljsbuild {:builds [{:id "client"
                        :source-paths ["src/shared"]
                        :figwheel true
                        :compiler {:main "om-fullstack-example.client"
                                   :asset-path "js/out"
                                   :output-to "resources/public/js/app.js"
                                   :output-dir "resources/public/js/out"}}]})
