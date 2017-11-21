(ns app.panel
  (:require
    [cljsjs.material-ui]
    [cljs-react-material-ui.core :as material
     :refer [get-mui-theme color]]
    [cljs-react-material-ui.reagent :as ui]
    [cljs-react-material-ui.icons :as ic]))

(def avatar-url "http://www.material-ui.com/images/jsa-128.jpg")

(defn content [session]
  [ui/paper {:style {:margin 10 :padding 10}}
   [ui/text-field {:floating-label-text "Favorite Language"}]
   [ui/card
    [ui/card-header {:title "Hotloaded"
                     :subtitle "Live coding for faster development"
                     :avatar avatar-url}]]])

(defn panel [session]
  [ui/paper {:style {:height "auto"}}
   [ui/app-bar {:title "MEkit Live Coding"}]
   [ui/paper {:style {:margin 10 :padding 10}}
    [ui/text-field {:floating-label-text "Favorite Language"}]
    [ui/card
     [ui/card-header {:title "Hotloaded"
                      :subtitle "Live coding for faster development"
                      :avatar avatar-url}]]]])
