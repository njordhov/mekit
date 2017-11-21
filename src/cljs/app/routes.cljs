(ns app.routes
  (:require-macros
   [cljs.core.async.macros
    :refer [go go-loop]])
  (:require
   [cljs.core.async :as async
    :refer [<!]]
   [goog.dom :as dom]
   [goog.events :as events]
   [goog.history.EventType :as EventType]
   [taoensso.timbre :as timbre]
   [secretary.core :as secretary
    :refer-macros [defroute]]
   [re-frame.core :as rf]
   [cljs-http.client :as http]
   [api.facebook.extensions :as ext]
   [app.session :as session])
  (:import
   [goog History]))

(secretary/set-config! :prefix "#")

(def history
  (memoize #(History.)))

(defn navigate! [token & {:keys [stealth]}]
  (if stealth
    (secretary/dispatch! token)
    (.setToken (history) token)))

(defn hook-browser-navigation! []
  (doto (history)
    (events/listen EventType/NAVIGATE
                   (fn [event]
                     (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update-extensions []
  (timbre/debug "update extensions"
                (ext/messenger-extensions-loaded?))
  (let [in (ext/fetch-supported-features) #_(ext/fetch-user-id)]
    (timbre/debug "user id channel:" in)
    (go
     (timbre/debug "user id...")
     (let [user-id (<! in)]
      (timbre/debug "user-id:" user-id)
      (when (some? user-id)
        (rf/dispatch [:insert [:user-id] user-id]))))))

(defroute "/" []
  (timbre/info "Home")
  (update-extensions))

(defroute "/share" []
  (rf/dispatch [:log "Share"])
  (try (ext/share-message ext/example-message
                          :report-error
                          (fn [code err]
                            (rf/dispatch [:log (str "Share error:" code err)])))
       (catch :default e
         (rf/dispatch [:log (str "Share failed:" e)]))))
