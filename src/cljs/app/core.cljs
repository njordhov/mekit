(ns app.core
  (:require-macros
   [cljs.core.async.macros
    :refer [go go-loop]])
  (:require
   [cljs.core.async :as async
    :refer [<! chan close! alts! timeout put!]]
   [goog.dom :as dom]
   [goog.events :as events]
   [goog.string :as gstring]
   [taoensso.timbre :as timbre]
   [reagent.core :as reagent
    :refer [atom]]
   [reagent.dom.server
    :refer [render-to-string]]
   [app.bridge :as bridge]
   [app.user :as user]
   [app.views
    :refer [view page html5]]
   [re-frame.core :as rf]
   [app.session :as session]
   [app.routes :as routes]))

(def scripts [#_{:src "//maps.googleapis.com/maps/api/js?key=AIzaSyC8IzTikjFG80Jj-U-0d12V1HuerTukLFs"}
              {:src "/js/out/app.js"}
              "main_cljs_fn()"])

(defn static-page []
    (go
      (-> {}
          (page :scripts scripts
                :title "MEkit"
                :forkme false)
          (render-to-string)
          (html5))))

(defn activate []
  (timbre/info "Activating app...")
  (session/initialize)
  (let [el (dom/getElement "canvas")
        session {:user-id (rf/subscribe [:session :user-id])}]
    (reagent/render [#(view session)] el))
  #_(routes/navigate! "/"))
