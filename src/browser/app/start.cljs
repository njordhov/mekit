(ns app.start
  (:require
   [taoensso.timbre :as timbre]
   [app.core :as app]
   [app.session :as session]
   [app.routes :as routes]
   [app.timbre-html-log :as timbre-html-log]))

(enable-console-print!)

(defn ^:export main []
  (timbre-html-log/enable)
  (timbre/info "Logging enabled")
  (app/activate)
  #_(timbre-html-log/toggle-html-log true)
  (routes/hook-browser-navigation!)
  #_(routes/navigate! "/" :stealth true))

(set! js/main-cljs-fn main)
