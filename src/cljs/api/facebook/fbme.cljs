(ns api.facebook.fbme
  (:require-macros
    [cljs.core.async.macros :as m :refer [go go-loop alt!]])
  (:require
    [cljs.core.async :as async
       :refer [chan <! >! put! close! timeout]]
    [taoensso.timbre :as timbre]))

(def ^{:doc "used as verify token when setting up webhooksecret-facebook-token"}
     secret-facebook-token
     (aget js/process "env" "FACEBOOK_VERIFY_TOKEN"))

(when-not secret-facebook-token
  (timbre/warn "Need to set the FACEBOOK_VERIFY_TOKEN environment var"))

(defn express-get-handler []
   ; see https://developers.facebook.com/docs/messenger-platform/guides/quick-start
  (fn [req res]
   (let [query (js->clj (.-query req))
         mode (get query "hub.mode")
         check-token #(assert (= secret-facebook-token
                                 (get query "hub.verify_token")))]
     (timbre/info "[FBME]get:" query (js->clj req))
     (case mode
       "subscribe" (do (check-token)
                       (timbre/debug "Validated webhook")
                       (.send res (get query "hub.challenge")))
       (do (timbre/error "Failed validation. Make sure the validation tokens match. mode:" mode)
           (.sendStatus res 403))))))

(defn express-post-handler [& [message-handler]]
  ; see https://developers.facebook.com/docs/messenger-platform/guides/quick-start
 (fn [req res]
  (timbre/debug "[FBME]post" (.keys js/Object req))
  (timbre/debug "[FBME]->" (aget req "body"))
  (if-let [data (aget req "body")]
    (let [object (.-object data)]
      (timbre/debug "[FBME]object" object)
      (case object
        "page" (doseq [entry (.-entry data)]
                 (let [id (.-id entry)
                       time (.-time entry)]
                   (timbre/debug "[FB] entry:" entry)
                   (doseq [event (.-messaging entry)]
                     (if (.-sender event)
                       (if message-handler
                         (message-handler (js->clj event :keywordize-keys true))
                         (timbre/warn "[FB] no handler for event"
                                      (js->clj event)))
                       (timbre/warn "[FB] unknown event "
                                    (js->clj event))))))
        (timbre/warn "[FBME] unknown object "
                     (js->clj object))))
    (timbre/warn "[FBME] no body in " (.-url req)(js-keys req)))
  (.sendStatus res 200)))
