(defproject mekit "0.7.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.562"]
                 [org.clojure/core.async "0.3.443"]
                 [com.taoensso/timbre "4.10.0"]
                 [camel-snake-kebab "0.4.0"]
                 [cljsjs/react "15.6.2-0"]
                 [cljsjs/react-dom "15.6.2-0"]
                 [cljsjs/react-dom-server "15.6.2-0"]
                 [cljsjs/create-react-class "15.6.2-0"]
                 [reagent "0.7.0"]
                 [re-frame "0.10.1"]
                 [cljs-http "0.1.43"]
                 [cljsjs/material-ui "0.19.2-0"
                  :exclusions [cljsjs/react-dom]] ;; tmp for react-material-ui
                 [cljs-react-material-ui "0.2.48"
                  :exclusions [cljsjs/material-ui
                               cljsjs/react-dom
                               org.clojure/clojure
                               org.clojure/clojurescript]]
                 #_
                 [cljsjs/google-maps "3.18-1"]
                 #_
                 [cljsjs/react-google-maps "6.0.1-0"
                  :exclusions [cljsjs/react
                               cljsjs/react-dom]]
                 [secretary "1.2.3"]
                 [kioo "0.5.0"
                  :exclusions [cljsjs/react
                               cljsjs/react-dom]]]

  :npm {:dependencies [[express "4.14.1"]
                       [xhr2 "0.1.4"]
                       [xmlhttprequest "*"]
                       [xmldom "0.1.27"]
                       [source-map-support "*"]
                       [react "15.6.1"];; "15.5.4"
                       #_
                       [react-dom "15.5.4"]]
        :root :root}

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.10"] ;; ## TODO: remove elsewhere
            [lein-npm "0.6.2"]]

  :min-lein-version "2.5.3"

  :hooks [leiningen.cljsbuild]

  :aliases {"start" ["npm" "start"]
            "test" ["with-profile" "test" "doo" "node" "server" "once"]}

  :main "main.js"

  :source-paths ["src/cljs"]

  :clean-targets ^{:protect false} [[:cljsbuild :builds :server :compiler :output-to]
                                    [:cljsbuild :builds :app :compiler :output-dir]
                                    "node_modules"
                                    :target-path :compile-path]

  :figwheel {:http-server-root "public"
             :server-port 3449
             :css-dirs ["resources/public/css"]
             :server-logfile "logs/figwheel.log"
             :load-all-builds false
             :builds-to-start [:app :server]}

  :cljsbuild {:builds
              {:app
               {:source-paths ["src/browser" "src/cljs"]
                :compiler {:output-to "resources/public/js/out/app.js"
                           :output-dir "resources/public/js/out"
                           :asset-path "js/out"
                           :main app.start
                           :optimizations :none}}

               :server
               {:source-paths ["src/node" "src/cljs"]
                :compiler {:target :nodejs
                           :output-to "main.js"
                           :output-dir "target"
                           :main server.core
                           :foreign-libs [{:file "src/node/polyfill/simple.js"
                                           :provides ["polyfill.simple"]}]
                           :optimizations :none}}}}


  :profiles {:dev
             {:plugins
              [[lein-figwheel "0.5.10"]
               [lein-doo "0.1.7"]]
              :cljsbuild
              {:builds
               {:app
                {:compiler {:pretty-print true
                            :source-map true}
                 :figwheel {:websocket-host "ad51b68b.ngrok.io"
                            :websocket-url "ws://ad51b68b.ngrok.io:80/figwheel-ws"
                            :on-jsload "reagent.core/force-update-all"}}
                :server
                {:compiler {:pretty-print true
                            :source-map true}
                 :figwheel {:heads-up-display false}}}}
              :npm {:dependencies [[ws "*"]]}}

             :test {:cljsbuild
                    {:builds
                     {:server
                      {:source-paths ["test"]
                       :compiler {:main runners.doo
                                  :optimizations :none;
                                  :output-to "target/test/server.js"
                                  :output-dir "target/test"}}}}}

             :production
             {:env {:production true}
              :cljsbuild
              {:builds
               {:server
                {:compiler {;:optimizations :simple
                            :pretty-print false}}
                :app
                {:compiler {;:output-dir "target/app/out"
                            ;:optimizations :advanced
                            :pretty-print false}}}}}})
