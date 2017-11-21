(ns app.views
  (:require-macros
   [kioo.reagent
    :refer [defsnippet deftemplate snippet]])
  (:require
   [kioo.reagent
    :refer [html-content content append after set-attr do->
            substitute listen unwrap]]
   [kioo.core
    :refer [handle-wrapper]]
   [reagent.core :as reagent
    :refer [atom]]
   [goog.string :as gstring]
   [cljsjs.material-ui]
   [cljs-react-material-ui.core :as material
    :refer [get-mui-theme color]]
   [cljs-react-material-ui.reagent :as ui]
   [cljs-react-material-ui.icons :as ic]
   [app.session :as session]
   [app.devconsole :as devconsole
    :refer [devconsole]]
   [app.panel :as panel
    :refer [panel]]
   [re-frame.core :as rf]))

(defn view [session]
  [ui/mui-theme-provider
   {:mui-theme (get-mui-theme
                {:palette
                 {:primary1-color (color :indigo900)
                  :primary2-color (color :red600)
                  :primary3-color (color :blue200)
                  :alternate-text-color (color :white) ;; used for appbar text
                  :primary-text-color (color :light-black)}})}
   [:div
    [panel session]
    [devconsole session]]])

(defsnippet page "template.html" [:html]
  [data & {:keys [scripts title forkme]}]
  {[:head :title] (if title (content title) identity)
   [:nav :.navbar-brand] (if title (content title) identity)
   [:main] (content [view data])
   [:#forkme] (if forkme identity (content nil))
   [:body] (append [:div (for [src scripts]
                           ^{:key (gstring/hashCode (pr-str src))}
                           [:script src])])})

(defn html5 [data]
  (str "<!DOCTYPE html>\n" data))

(defn test-views []
  (html5 (page ["Chuck Norris eats parentheses for breakfast"])))
