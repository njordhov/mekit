(ns app.devconsole
  (:require
   [cljsjs.material-ui]
   [cljs-react-material-ui.core :as material
    :refer [get-mui-theme color]]
   [cljs-react-material-ui.reagent :as ui]
   [cljs-react-material-ui.icons :as ic]
   [app.timbre-html-log
    :refer [timbre-html-element
            timbre-html-log
            toggle-html-log]]))

(defn raw [value]
  [:div.raw {:style {:word-break "break-all"}}
     [:pre (pr-str value)]])

(defn session-view [session]
  (into [:div {:style {:border "thin solid red"
                       :min-height "1em"}}]
        (for [[k v] session]
          [:div
           [raw [k @v]]])))

(defn devconsole [session]
  [ui/drawer {:open (:shown @timbre-html-log)
              :on-request-change #(toggle-html-log %)
              :open-secondary true
              :width 300}
   ;:div {:style {:position "fixed" :left 0 :top 0 :z-index 100}}
   [ui/toolbar {}
    [ui/toolbar-title {:text "Developer Console: Log"}]]
   [timbre-html-element]])
