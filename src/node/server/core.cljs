(ns server.core
  (:require-macros
   [cljs.core.async.macros :as m
    :refer [go go-loop alt!]])
  (:require
   [polyfill.compat]
   [cljs.nodejs :as nodejs]
   [cljs.core.async :as async
    :refer [chan close! timeout put!]]
   [taoensso.timbre :as timbre]
   [reagent.core :as reagent
    :refer [atom]]
   [api.facebook.fbme :as fbme]
   [app.core :as app
    :refer [static-page]]
   [server.setup :as setup]))

(enable-console-print!)

(def express (nodejs/require "express"))

(defn handler [req res]
  (if (= "https" (aget (.-headers req) "x-forwarded-proto"))
    (.redirect res (str "http://" (.get req "Host") (.-url req)))
    (go
      (.set res "Content-Type" "text/html")
      (.send res (<! (static-page))))))

(defn debug-redirect [req res]
   (let [local (aget js/process "env" "REDIRECT")]
     (when-not (empty? local)
       (timbre/debug "REDIRECT:" (str local (.-url req)))
       (.redirect res 307 (str local (.-url req)))
       true)))

(defn wrap-intercept [handler]
  (fn [req res]
    #_(timbre/debug "REDIRECT?" (.keys js/Object req))
    (or (debug-redirect req res)
        (handler req res))))

(defn fbme-handler [request]
   (timbre/debug "FBME->" request)
   (when-let [text (get-in request [:message :text])]
     (when-not (get-in request [:message :is_echo])
       (timbre/debug "FBME=>" text request))))


(defn server [port success]
  (doto (express)
        (.get "/" (wrap-intercept handler))
        (.get "/fbme/webhook" (wrap-intercept
                               (fbme/express-get-handler)))
        (.post "/fbme/webhook" (wrap-intercept
                                (fbme/express-post-handler fbme-handler)))
        (.use (.static express "resources/public"))
        (.listen port success)))

(defn -main [& mess]
  (when (not= (aget js/React "version")
              (aget (reagent.dom.server/module) "version"))
    (timbre/warn "Inconsistent React version:"
      (aget js/React "version")
      (aget (reagent.dom.server/module) "version")))
  (let [port (or (.-PORT (.-env js/process)) 1337)]
    (server port
            #(println (str "Server running at http://127.0.0.1:" port "/")))))

(set! *main-cli-fn* -main)
