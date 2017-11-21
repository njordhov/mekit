(ns server.setup
  (:require
   [cljs-http.client :as http]
   [app.lib :as lib]))

(def fb-endpoint "https://graph.facebook.com/v2.10/")
(def fb-access-secret (aget js/process "env" "FACEBOOK_ACCESS_TOKEN"))
(def fb-profile-endpoint (str fb-endpoint "me/messenger_profile?access_token="
                              fb-access-secret))


(defn configure-persistent-menu []
  (http/post fb-profile-endpoint
                 {:json-params
                  {"persistent_menu"
                   [{"locale" "default"
                     ; "composer_input_disabled" false
                     "call_to_actions"
                     [{:type "web_url"
                       :title "Rapid Care"
                       :url "https://rapidcare.herokuapp.com"
                       :webview_height_ratio "compact"
                       :messenger_extensions true}]}]}}))

#_
(lib/echo
 (configure-persistent-menu))
