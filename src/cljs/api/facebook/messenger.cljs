(ns api.facebook.messenger
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [cljs.core.async :as async
    :refer [chan <! >! put! close! timeout]]
   [clojure.spec.alpha :as s]
   [taoensso.timbre :as timbre]
   [goog.net.XhrIo :as xhr]
   [camel-snake-kebab.core
    :refer [->snake_case_keyword
            ->snake_case_string
            ->kebab-case-keyword]]
   [camel-snake-kebab.extras :refer [transform-keys]]))

#_
(set! *warn-on-infer* true)

;; ## factor out the transport to eliminate xhr
;; ## and make it more flexible using protocols?

;; Design: favor composing messages using middleware rather than specialied send functions
;; but keep a few specialzied functions around to see whether its more practical.

;;    Docs:
;;    https://developers.facebook.com/docs/messenger-platform/send-api-reference
;;
;;    compare to:
;;    https://www.npmjs.com/package/fb-messenger
;;    https://www.npmjs.com/package/facebook-send-api **
;;    https://www.npmjs.com/package/botly
;;    https://www.npmjs.com/package/bootbot-pl
;;    https://www.npmjs.com/package/messenger-bot

(def fb-endpoint "https://graph.facebook.com/v2.9/")
(def fb-messages-endpoint (str fb-endpoint "me/messages"))
(def fb-profile-endpoint (str fb-endpoint "me/messenger_profile"))

(def fb-access-secret
  (aget js/process "env" "FACEBOOK_ACCESS_TOKEN"))

(def test-id "...") ;; eliminate
#_(def sender-id "")  ;; page-id
#_(def recipient-id "1301247523276279")

(let [snake_case (memoize ->snake_case_keyword)] ;; TODO: eliminate
  (defn snake_case_keys [m]
    (into {} (for [[k v] m] [(snake_case k) v]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SPEC
;;
;; https://developers.facebook.com/docs/messenger-platform/send-api-reference

;; ## Eliminate the underscore in keywords?
;; ## avoid unqualified keywords?
;; any way to declare that request can contain _either_ message or sender_action

(defn check
  ([type value]
   (if-not (s/valid? type value)
      (do (timbre/error (s/explain-str type value)) false)
      true)))

(defn check-event [value]
  (check ::event value))

;; --------------------------------------
;; EVENT is the object received from facebook via the webhook
;; See https://developers.facebook.com/docs/messenger-platform/webhook-reference
;; TODO: These are in the .entry.messaging list of the entry object from the hook
;; TODO: Consider factoring to separate module handling incoming events

(s/def ::event (s/and (s/keys :req-un [::sender ::recipient ::timestamp])
                      (s/or
                       :postback (s/keys :req-un [::postback])
                       :any (s/keys :opt-un []))))

#_
(def postback-xmp {:sender {:id "USER_ID"}
                   :recipient {:id "PAGE_ID"}
                   :timestamp 1458692752478
                   :postback {:payload 'USER_DEFINED_PAYLOAD
                              :referral {:ref 'USER_DEFINED_REFERRAL_PARAM
                                         :source "SHORTLINK"
                                         :type "OPEN_THREAD"}}})

#_
(s/valid? ::event postback-xmp)
#_
(s/describe ::event)

;; --------------------------------------
;; REQUEST is the object sent to facebook

(s/def ::request (s/or
                   :whitelist (s/keys :req-un [::whitelisted_domains])
                   :home-url (s/keys :req-un [::home_url])
                   :get-started (s/keys :req-un [::get_started])
                   :persistent-menu (s/keys :req-un [::persistent_menu])
                   :envelope
                   (s/keys :req-un [::recipient]
                           :opt-un [::sender_action ::message ::notification_type])))

(s/def ::whitelisted_domains (s/coll-of ::url :max-count 10))

(s/def ::home_url (s/keys :req-un [::url ::webview_height_ratio ::in_test]
                          :opt-un [::webview_share_button]))

(s/def ::get_started (s/keys :req-un [::payload]))

(s/def ::persistent_menu (s/coll-of (s/keys :opt-un [])))

(s/def ::url string?)

(s/def ::recipient (s/keys :req-un []
                           ;; phone_number or id must be set
                           :opt-un [::id ::phone_number ::name]))

(s/def ::id string?)

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/sender-actions

(s/def ::sender-action #{:mark_seen :typing_on :typing_off})

(s/def ::message (s/keys :req-un []
                         :opt-un [::text ::attachment ::quick_replies ::metadata]))

(s/def ::metadata string?)  ;; limited to 1000 chars

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/quick-replies

(s/def ::quick_replies (s/coll-of ::quick-reply))

(defmulti quick-reply-type (comp vector :content_type))

(s/def ::quick-reply (s/multi-spec quick-reply-type :content_type))

(s/def ::content-type #{:location :text})

(defmethod quick-reply-type [:location] [_]
  (s/keys :req-un [::content_type]
          :opt-un [::title ::payload ::image_url]))

(defmethod quick-reply-type [:text] [_]
  (s/keys :req-un [::content_type ::title ::payload]
          :opt-un [::image_url]))

;;;;

(s/def :attachment/type #{:audio :file :image :video :template})

#_
(s/valid? :attachment/type :file)

(defmulti attachment-type (comp vector :type))

(s/def ::attachment (s/multi-spec attachment-type :type))

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/audio-attachment

(defmethod attachment-type [:audio] [_]
  (s/keys :req-un [:attachment/type :audio-attachment/payload]))

(s/def :audio-attachment/payload (s/keys :req-un [::url]))

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/file-attachment

(defmethod attachment-type [:file] [_]
  (s/keys :req-un [:attachment/type :file-attachment/payload]))

(s/def :file-attachment/payload (s/keys :req-un [::url]))

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/image-attachment

(defmethod attachment-type [:image] [_]
  (s/keys :req-un [:attachment/type :image-attachment/payload]))

(s/def :image-attachment/payload (s/keys :req-un [::url]))

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/video-attachment

(defmethod attachment-type [:video] [_]
  (s/keys :req-un [:attachment/type :video-attachment/payload]))

(s/def :video-attachment/payload (s/keys :req-un [::url]))

#_
(s/explain ::attachment {:type :audio :payload {:url "http://audio.com"}})

#_
(attachment-type {:type :file})

#_
(s/explain ::attachment {:type :file
                         :foo :bar})
#_
(s/valid? ::attachment {:type :file})

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/templates

(defmethod attachment-type [:template] [_]
  (s/keys :req-un [:attachment/type :template-attachment/payload]))

(defmulti payload-type (comp vector :template_type))

(s/def :template-attachment/payload (s/multi-spec payload-type :template_type))

(s/def ::template_type #{:button :generic :list :receipt :airline_boardingpass
                         :airline_checkin :airline_itinerary :airline_update})

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/button-template

(defmethod payload-type [:button] [_]
  (s/keys :req-un [::template_type ::text ::buttons]))

(s/def ::buttons (s/coll-of ::message-button :min-count 1 :max-count 3))

(s/def ::message-button (s/and ::button))

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/generic-template

(defmethod payload-type [:generic] [_]
  (s/keys :req-un [::template_type :generic/elements]
          :opt-un [:generic/image-aspect-ratio ::sharable]))

(s/def :generic/image-aspect-ratio #{:horizontal :square})

(s/def :generic/elements (s/coll-of :generic/element :max-count 10))

(s/def ::sharable false?)

(s/def :generic/element
  (s/keys :req-un [::title]
          :opt-un [::subtitle ::image_url ::default_action :generic/buttons]))

(s/def ::default_action (s/keys :req-un [:default_action/type
                                         ::url]
                                :opt-un [::webview_height_ratio
                                         ::messenger_extensions
                                         ::fallback_url]))

(s/def :default_action/type #{:web_url})

(s/def :generic/buttons (s/coll-of :generic/button :max-count 3))

(s/def :generic/button (s/and ::button))

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/list-template

(defmethod payload-type [:list] [_]
  (s/keys :req-un [::template_type :list/elements]
          :opt-un [:list/top_element_style :list/buttons]))

(s/def :list/elements (s/coll-of :generic/element :min-count 2 :max-count 4))

(s/def :list/top_element_style #{:large :compact})

(s/def :list/buttons (s/coll-of :list/button :max-count 1))

(s/def :list/button (s/and ::button))

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/buttons

(s/def :button/type #{:web_url :postback :phone_number :element_share :payment
                      :account_link :account_unlink})

(defmulti button-type (comp vector :type))

(s/def ::button (s/multi-spec button-type :type))

;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/url-button

(defmethod button-type [:web_url] [_]
  (s/or :web-url
        (s/keys :req-un [:button/title :button/type ::url]
                :opt-un [::webview_height_ratio])
        :messenger-extensions
        (s/keys :req-un [:button/title
                         :button/type
                         :messenger-extensions/url]
                :opt-un [::webview_height_ratio
                         ::messenger_extensions
                         ::fallback_url])))

(defn button-title? [s]
  (and (string? s)
       (<= (count s) 20)))

(s/def :button/title button-title?)

(s/def ::webview_height_ratio #{"compact" "tall" "full"})

(s/def ::messenger_extensions true?)

(defn https-url? [s]
  (and (string? s)
       (clojure.string/starts-with? s "https:")))

(s/def :messenger-extensions/url https-url?)

(s/def ::fallback_url string?) ;; only when messenger-extensions is true

(defmethod button-type [:postback] [_]
  (s/keys :req-un [:button/type ::title ::payload])

  (defmethod button-type [:element_share] [_]
    (s/keys :req-un [:button/type])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; INCOMING events




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TRANSFER

(defn result-handler [result-chan & [kebab-case?]]
  ;; ## TODO: kebab-case should be default and eliminated!
  (fn [e]
    (if (-> ^js/Event e .-target .isSuccess)
      (put! result-chan
            (if kebab-case?
              (->> ^js/Event e .-target .getResponseJson js->clj
                  (transform-keys ->kebab-case-keyword))
              (-> ^js/Event e .-target .getResponseJson
                  (js->clj :keywordize-keys true))))
      (timbre/error (-> ^js/Event e .-target .getLastError)
                    (-> ^js/Event e .-target .getResponseJson
                       (js->clj :keywordize-keys true))))
    (close! result-chan)))

;; ## need to handle errors in particular rate limits

(defn api-post [{:as event} uri & [kebab-case?]]
  ;; ## TODO: kebab-case should be default and eliminated!
   (go-loop [method "POST"
             content  (->> event
                           (transform-keys ->snake_case_keyword)
                           (clj->js)
                           (goog.json.serialize))
             headers #js {"Content-Type" "application/json"}
             result (chan)
             cb (result-handler result kebab-case?)]
     (xhr/send uri cb method content headers)
     (<! result)))

(defn api-get [uri & [kebab-case?]]
  ;; ## TODO: kebab-case is default!
  (let [result (chan)
        cb (result-handler result kebab-case?)]
     (xhr/send uri cb)
     result))

(defn send
  ([{:as request} endpoint]
   {:pre [(check ::request request)]}
   (api-post request (str endpoint "?access_token=" fb-access-secret)))
  ([request]
   (send request fb-messages-endpoint)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DESTRUCTURING messages

(defn get-sender-id [event]
  {:post [#(check ::id %)]}
  (get-in event [:sender :id]))

(def get-message-id get-sender-id) ;; ## deprecated to be eliminated

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; COMPOSING MESSAGES

(defrecord Request [])

(defn set-recipient [root id]
  [:pre [(check ::id id)]]
  (assoc-in root [:recipient :id] id))

(defn create-request
  ([id]
   (set-recipient (create-request) id))
  ([] (->Request)))

#_
(check ::request (create-request "123"))

(def create-message create-request) ;; deprecated

(defn create-reply [event]
  {:pre [(check ::event event)]}
  (-> (get-message-id event)
      (create-request)))

(defn set-action [root action]
  (assoc-in root [:sender_action] action))

(defn set-message-text [root text]
      (assoc-in root [:message :text] text))

(defn set-message-replies [root options]
  (assoc-in root [:message :quick_replies] options))

(defn fb-wrap-quick-replies [root replies]
      (assoc-in root [:message :quick_replies]
                     (for [item replies
                           :let [item (if (map? item) item {:title (str item)})]]
                       (-> item
                           (update :content_type #(or % "text"))
                           (update :payload
                                   #(or % (if (map? item)(:title item)item)))))))

(defn set-attachment-type [root type]
  (assoc-in root [:message :attachment :type] type))

(defn set-payload-template-type [root type] ;; #eliminate?
  (assoc-in root [:message :attachment :payload :template_type] type))

(defn set-payload-elements [root elements] ;; #eliminate
  (assoc-in root [:message :attachment :payload :elements] elements))

(defn set-message-metadata [root metadata]
  (assoc-in root [:message :metadata] metadata))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; payload

(defn as-buttons-payload [text buttons]
  (assoc {}
         :template_type :button
         :text text
         :buttons buttons))

(defn as-generic-payload [elements & {:keys [top_element_style buttons] :as opt}]
  (assoc opt
         :template_type :generic
         :elements elements))

(defn as-list-payload [elements]
     {:template_type :list
      :elements elements})

;; buttons - ## perhaps be a bit more clever here?

(defn url-button [title & {:keys [url webview-height-ratio messenger-extensions
                                  fallback-url webview-share-button]
                           :as options}]
  (->> options
       (transform-keys ->snake_case_keyword)
       (merge {:type :web_url :title title})
       (remove #(nil? (second %)))
       (into {})))

(defn postback-button [title & {:keys [payload]}]
  {:type :postback :title title :payload payload})

(defn share-button [& {:keys [share_contents]}]
  {:type :element_share :share_contents share_contents})

;; reply

(defn text-reply [title payload & {:keys [image_url] :as opt}]
  (assoc opt
         :content_type :text
         :title title
         :payload payload))

(defn location-reply []
  {:content_type :location})

;;

(defn create-element [title & {:keys [subtitle image-url
                                      default-action buttons] :as options}]
  (->>
   {:title title}
   (merge options)
   (remove (comp nil? second))
   (into {})
   (snake_case_keys)))

(defn set-element-buttons [element buttons]
  (assoc element :buttons buttons))

(defn set-template-payload
    ([root payload]
     {:pre [(check :template-attachment/payload payload)]}
     (-> (set-attachment-type root :template)
         (assoc-in [:message :attachment :payload] payload)))
    ([root type payload1 & payload]
     (let [generator (case type
                       :generic as-generic-payload)]
       (->> (apply generator payload1 payload)
            (set-template-payload root)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SPECIALZIED SENDERS (#should generally be avoided in favor of composing?)

(defn send-text [id text]
  (-> (create-message id)
      (set-message-text text)
      (send)))

(defn send-action [id action]
  (-> (create-message id)
      (set-action action)
      (send)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MESSENGER PROFILE API
;;
;; https://developers.facebook.com/docs/messenger-platform/reference/messenger-profile-api/

(defn get-profile [fields]
  (->> (map ->snake_case_string fields)
       (interpose ",")
       (str fb-profile-endpoint
            "?access_token=" fb-access-secret "&fields=")
       #_
       (api-get)))

#_
(get-profile ["whitelisted_domains"])

(defn post-profile [& {:as fields}]
  (send fields fb-profile-endpoint))

;; https://developers.facebook.com/docs/messenger-platform/webview/extensions))

(defn send-whitelist-domains
  "To use Messenger Extensions in your bot, you must first whitelist the domain the page is served from"
  ([[:as domains]]
   (post-profile :whitelisted_domains
                 (vec domains))))

;; https://developers.facebook.com/docs/messenger-platform/messenger-profile/home-url/

(defn send-home-url
  "enable a Chat Extension in the composer drawer in Messenger. It controls what the user sees when your bot's chat extension is invoked via the composer drawer in Messenger."
  ([{:keys [url webview_height_ratio webview_share_button in_test]
     :or {in_test true webview_height_ratio "tall"} :as home-url}]
   (timbre/debug "URL=" home-url)
   (post-profile :home_url home-url)))

#_
(app.lib/echo (send-home-url
               {:in_test true
                :webview_height_ratio "tall"
                :url "https://chroncare2.herokuapp.com/extension.html"}))

;; https://developers.facebook.com/docs/messenger-platform/user-profile

(defn send-get-started
  ([{:keys [payload] :as get-started}]
   (post-profile :get_started get-started)))

(def get-started-payload "START") ;; need to post-profile below on changes

#_ ; "https://developers.facebook.com/docs/messenger-platform/messenger-profile/get-started-button"
(app.lib/echo (post-profile :get_started {:payload get-started-payload}))

#_
(->> [{:locale "default"
       :call_to_actions [{:type "web_url",
                          :title "Fresh Forecast"
                          :url "https://chroncare2.herokuapp.com/extension.html"
                          :messenger-extensions true
                          :webview-height-ratio "full"}]}]
     (post-profile :persistent_menu)
     (app.lib/echo))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fetch-user-profile [id]
  {:pre [(string? id)]}
  ; first_name,last_name,profile_pic,locale,timezone,gender
  (api-get (str fb-endpoint id "?access_token=" fb-access-secret) true))

#_
(app.messaging.pubnub/echo (fetch-user-profile test-id))

(defn fetch-user-pageids [id]
  (api-get (str fb-endpoint id "/ids_for_pages" "?access_token=" fb-access-secret)))



(defn send-echo [event]
  ;; send message text back to sender as is (useful for testing)
  (-> (create-reply event)
      (set-message-text (or (get-in event [:message :text])
                            (get-in event [:message :message :text])))
      (send)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PERSISTENT MENU

(defn send-persistent-menu [[:as menu-items]]
  (post-profile :persistent_menu menu-items))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; GREETING

(defn add-greeting [text]
  (send {:setting_type "greeting"
         :greeting {:text text}}
        (str fb-endpoint "me/thread_settings")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; conversation
;;
;; https://developers.facebook.com/docs/graph-api/reference/v2.9/conversation

;; experimental, not yet verified to work!!

;; has to be a "conversation id", doesn't work with thread id after fbapi 2.5

(defn fetch-conversation [conversation-id]
  (api-get (str fb-endpoint conversation-id
                "?access_token=" fb-access-secret)))

(defn fetch-conversation-messages [conversation-id]
  (api-get (str fb-endpoint conversation-id "/messages"
                "?access_token=" fb-access-secret)))

#_
(app.lib/echo (fetch-conversation "1384101711657948"))

; tid = 1384101711657948
; psid = 1301247523276279

#_
(app.lib/echo (fetch-conversation-messages "1384101711657948"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TESTS

(defn test1 []
  (send-text test-id"Hello!"))

;; constrasting ways to build the element...

(defn test-buttons []
  (-> (create-message test-id)
      (set-template-payload
       (as-buttons-payload "Do you perceive any changes?"
                           [(postback-button "Better" :payload "better")
                            (postback-button "Same" :payload "same")
                            (postback-button "Worse" :payload "worse")]))
      (send)))

(def my-payload-elements
  [(-> (create-element "FlareCast")
       (set-element-buttons [(url-button "open sesame"
                                 :url "http://predictablywell.com")
                             (postback-button "PostBack"
                                 :payload "payload for button")]))
   (create-element  "FlareCare"
          :buttons [(url-button "Hello"
                          :url "http://predictablywell.com/foo")
                    (postback-button "PostBack2"
                          :payload "payload for button2")])])

(defn test-generic []
  (-> (create-message test-id)
      (set-template-payload
        (as-generic-payload my-payload-elements))
      (send)))

(defn test-list []
  (-> (create-message test-id)
      (set-template-payload
        (as-list-payload my-payload-elements))
      (send)))

(defn test-locate []
  (-> (create-message test-id)
      (set-message-text "Where in the world is Carmen Sandiego?")
      (set-message-replies [(location-reply)])
      (send)))

(defn test-replies []
  (-> (create-message test-id)
      (set-message-text "Do you have a flare?")
      (set-message-replies [(text-reply "yes" "Y")
                            (text-reply "no" "N")
                            (location-reply)])
      (send)))



(defn replies-n [id q n]
  (-> (create-message id)
      (set-message-text q)
      (set-message-replies (for [i (range 1 (inc n))]
                             (text-reply (str i) (str i))))))

(defn test-replies-n [q n]
  (replies-n test-id q n))

(defn test-action []
  (go
   (<! (timeout 5000))
   (send-action test-id :mark_seen)
   (<! (timeout 1000))
   (send-action test-id :typing_on)
   (<! (timeout 7000))
   (send-action test-id :typing_off)))

#_
(defn test-webview []
  (-> (create-message test-id)
      (set-template-payload
       (as-generic-payload [(-> (create-element "ChronCare")
                                (set-element-buttons [(url-button "open webview"
                                                          :url chroncare-web)]))]))
      (send)))

#_
(defn test-webview2 []
  (send-whitelist-domains ["https://chroncare2.herokuapp.com"])
  (-> (create-message test-id)
      (set-template-payload
        (as-generic-payload [(-> (create-element "ChronCare 2")
                                 (set-element-buttons [{:type :web_url
                                                        :title "open"
                                                        :fallback_url "https://predictablywell.com"
                                                        :url chroncare-web
                                                        :webview_height_ratio "full"
                                                        :messenger-extensions true}]))]))

      (send)))
