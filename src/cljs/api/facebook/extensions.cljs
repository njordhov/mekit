(ns api.facebook.extensions
  (:require-macros
    [cljs.core.async.macros :as m
      :refer [go go-loop alt!]])
  (:require
    [cljs.core.async :as async
      :refer [chan <! >! put! close! timeout]]
    [taoensso.timbre :as timbre]
    [camel-snake-kebab.core
     :refer [->snake_case_keyword ->kebab-case-keyword]]
    [camel-snake-kebab.extras :refer [transform-keys]]
    [api.facebook.messenger :as me]
    [app.lib :as lib]))

(set! *warn-on-infer* true)

(def promise-chan chan)

(defn success-fn
  ([port]
   (success-fn port identity))
  ([port extract]
   (fn [& rest]
     (try
       (when-let [result (if (and extract rest)
                           (apply extract rest)
                           true)]
         (timbre/info result)
         (put! port result)
         (close! port))
       (catch :default e
         (timbre/error e)
         (close! port))))))

(defn error-fn
  ([port & [report]]
   (fn [errorCode errorMessage & rest]
     (if report
       (report errorCode errorMessage)
       (timbre/warn errorCode errorMessage))
     (close! port))))

(defn clj->snake_case_js [clj]
  (clj->js (transform-keys ->snake_case_keyword clj)))

(defn js->kebab-case-clj [js]
  (->> (js->clj js :keywordize-keys true)
       (transform-keys ->kebab-case-keyword)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn url-button [title & {:keys [url] :as options}]
  (when (clojure.string/starts-with? url "http:") ;; ## covered by spec?
    (timbre/warn "Messenger extension URL button requires https method" url))
  (apply me/url-button title (apply concat options)))

(defn url-action [& {:keys [url] :as options}]
  (apply url-button nil (apply concat options)))

(def create-element me/create-element)

(defn generic-attachment [{:keys [elements image-aspect-ratio sharable]
                           ; :or {sharable true}
                           :as payload}]
  {:post [(me/check (me/attachment-type {:type :template}) (:attachment %))]}
  ;; doesn't post (even if it shows up for verification) possibly due to the 'generic' type
  ;; in elements these are optional: subtitle and default-action
  ;; ## Todo: factor tests into subtype declaration:
  (when-not (= 1 (count elements))
    (timbre/warn "Message extension requires exactly one element in generic messages"))
  (when-not (>= 1 (count (get-in elements [0 :buttons])))
    (timbre/warn "Message extension allows no more than one button in an generic messages element"))
  (when-not (every? #(= :web_url (:type %))
                    (get-in elements [0 :buttons]))
    (timbre/warn "Message extension requires a web url in the generic attachment payload"))
  {:attachment {:type :template
                :payload (->> payload
                              (merge {:template_type :generic})
                              (remove #(nil? (second %)))
                              (into {}))}})

;; working example from https://developers.facebook.com/docs/messenger-platform/webview/sharing/v2.9

(def example-element (create-element
                      "I took Peter's 'Which Hat Are You?' Quiz"
                      :image-url "https://chroncare2.herokuapp.com/media/juliet-avatar.png"
                      :subtitle "My result: Fez"
                      :default-action {:type :web_url
                                       :url "https://bot.peters-hats.com/view_quiz_results.php?user=24601"}
                      :buttons[(url-button
                                 "Again!"
                                 :url "https://bot.peters-hats.com/hatquiz.php?referer=24601")]))


(def example-message
  (generic-attachment {:elements [example-element]}))


#_ (identity example-message)

(defn image-message [image-url] ; doesn't work for extension
  ;; https://developers.facebook.com/docs/messenger-platform/send-api-reference/image-attachment
  {:attachment {:type "image"
                :payload {:url image-url}}})

(defn share-message  ;; rename to just 'share?
  "Note that several restrictions on content apply when using this API"
  ; https://developers.facebook.com/docs/messenger-platform/webview/sharing/v2.9
  ; seems to require at least one button...
  ([message & {:keys [mode report-error] :or {mode "current_thread"}}]
   {:pre [(string? mode)]
    :post [some?]}
   (let [out (promise-chan)
         extract #(try (.-is_sent ^js/Facebook.Messenger %)
                       (catch :default e
                         (if report-error
                           (report-error "Failed to extract:" e)
                           (timbre/error "Failed to extract:" e))
                         false))
         content (clj->snake_case_js message)]
     (timbre/debug "BEGINSHAREFLOW:" (js->clj content))
     (.beginShareFlow ^js/Facebook.Messenger js/MessengerExtensions
                      (success-fn out extract)
                      (error-fn out
                                (fn [errorCode errorMessage]
                                  (if report-error
                                    (report-error "Failed to share:"
                                                  errorCode errorMessage)
                                    (timbre/error "Failed to share:"
                                                  errorCode errorMessage))))
                      content
                      (or mode "current_thread"))
     out)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn messenger-extensions-loaded? []
  (if js/MessengerExtensions true false))

(defn fetch-context
  "https://developers.facebook.com/docs/messenger-platform/webview/threadcontext"
  ([app-id]
   {:pre [(string? app-id)]}
   (let [out (promise-chan)
         extract js->kebab-case-clj]
     (.getContext ^js/Facebook.Messenger js/MessengerExtensions
                  app-id
                  (success-fn out extract)
                  (error-fn out))
     out)))

(defn fetch-user-id []
  {:pre [(messenger-extensions-loaded?)]}
  "get id to identify and authenticate the user and personalize the resulting experience"
  ;; https://developers.facebook.com/docs/messenger-platform/webview/userid
  (let [out (promise-chan)
         extract #(.-psid ^js/Facebook.Messenger %)]
     (.getUserID ^js/Facebook.Messenger js/MessengerExtensions
                 (fn success [uids]
                   (try
                     (do
                       (timbre/debug "UIDS:" (js->clj uids))
                       (timbre/debug "PSID:" (extract uids))
                       (put! out (extract uids)))
                     (catch :default e
                       (timbre/error "Error:" (pr-str e))))
                   (close! out))
                 (error-fn out))
     out))

(defn request-close-browser []
  (let [out (promise-chan)]
    (.requestCloseBrowser ^js/Facebook.Messenger js/MessengerExtensions
                          (success-fn out)
                          (error-fn out))
    out))

;; for testing

(defn get-supported-features [success error]
  (.getSupportedFeatures ^js/Facebook.Messenger js/MessengerExtensions
                         success
                         error))

#_
(get-supported-features #(timbre/info %) #(timbre/error %))

(defn fetch-supported-features []
  ; https://developers.facebook.com/docs/messenger-platform/webview/supported-features
  (let [out (promise-chan)
        extract #(->> (.-supported_features ^js/Facebook.Messenger %)
                      (map ->kebab-case-keyword)
                      (set))]
    (.getSupportedFeatures ^js/Facebook.Messenger js/MessengerExtensions
                           (success-fn out extract)
                           (error-fn out))
    out))

(defn ask-permission [permission-name]
  ;; https://developers.facebook.com/docs/messenger-platform/webview/permissions/
  {:pre [(string? permission-name)]}
  (let [out (promise-chan)]
    (.askPermission ^js/Facebook.Messenger js/MessengerExtensions
                    (success-fn out js->clj)
                    (error-fn out)
                    permission-name)
    out))

(defn fetch-granted-permissions []
  ;; https://developers.facebook.com/docs/messenger-platform/webview/permissions/
  (let [out (promise-chan)
        extract #(js->clj (.-permissions ^js/Facebook.Messenger %))]
    (.getGrantedPermissions ^js/Facebook.Messenger js/MessengerExtensions
                    (success-fn out js->clj)
                    (error-fn out))
    out))
