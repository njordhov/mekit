(ns app.session
  (:require
   [cljs.core.async :as async
    :refer [<!]]
   [taoensso.timbre :as timbre]
   [reagent.core :as reagent]
   [re-frame.core :as rf]))

(def defaults {})

(defn initialize [& session]
  (rf/reg-event-db
   :initialize
   (fn [_ _]
     (timbre/debug "[INITIALIZE]")
     (merge defaults session))))

(rf/reg-event-db
  :insert
  (fn [db [_ k value]]
    (timbre/debug "[INSERT]" k)
    (assoc-in db k value)))

(rf/reg-sub
 :session
 (fn [db [_ & path]]
   (timbre/debug "[SUB]" path)
   (if (empty? path)
     db
     (get-in db (vec path)))))

(rf/reg-event-db
  :log
  (fn [db [_ value]]
    (timbre/debug "[LOG]" value)
    (update db :log
            conj value)))

(rf/reg-sub
 :log
 (fn [db [_]]
   (:log db)))
