(ns app.timbre-html-log
  (:require
   [goog.dom :as dom]
   [goog.string :as gstring]
   [taoensso.timbre :as timbre]
   [reagent.core :as reagent
      :refer [atom]]))

(defonce timbre-html-log (reagent/atom {:content []
                                        :shown false}))

(defn toggle-html-log
  ([shown] (swap! timbre-html-log assoc :shown shown))
  ([] (swap! timbre-html-log update :shown not)))

(defn timbre-html-element
  ([] [timbre-html-element @timbre-html-log])
  ([{:keys [content shown]}]
   (let [value (reagent/atom "")
         pattern (reagent.ratom/reaction
                    (try
                       (re-pattern @value)
                       (catch :default e nil)))]
     (fn [{:keys [content shown]}]
       [:div {:style {:display (if-not shown "none")}}
        [:input {:type "text"
                 :value @value
                 :on-change #(reset! value (-> % .-target .-value))
                 :style {:width "100%"
                         :border (if (nil? @pattern)
                                   "thin solid red")}}]
        [:ol {:reversed true
              :style {:max-width "100%"
                      :margin-top "10"
                      :word-wrap "break-word"}}
         (doall (for [[ix line] (reverse (map-indexed vector content))
                      :when (re-find (or @pattern #"") line)]
                  ^{:key (str ix)}
                  [:li line]))]]))))

(defn inject-timbre-log [id]
  (->> (dom/getElement id)
       (reagent/render-component
        [timbre-html-element])))

(defn timbre-html-appender
  [& [{:keys [id]}]]
  {:pre [(or (nil? id)(string? id))]}
  (let [report (fn [s]
                 (swap! timbre-html-log update :content conj s))]
    (when id
      (inject-timbre-log id))
    {:enabled?   true
     :async?     true
     :min-level  nil
     :rate-limit nil
     :output-fn :inherit
     :fn (fn [{:keys [instant level msg_ output_ ?ns-str ?line] :as data}]
           (timbre/with-config timbre/example-config
             (try
               (report (force output_))
               (catch :default e
                 (timbre/error "Timbre HTML appender failed:" id e)))))}))

(defn enable [& [id]]
  (timbre/merge-config!
   {:appenders
     {:html (timbre-html-appender {:id id})}}))
