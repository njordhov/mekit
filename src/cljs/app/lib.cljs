(ns app.lib
  (:require-macros
   [cljs.core.async.macros
    :refer [go go-loop]])
  (:require
   [cljs.core.async :as async
    :refer [<! chan close! alts! timeout put!]]
   [taoensso.timbre :as timbre]))

(defn echo [ch]
  (go-loop []
    (when-let [item (<! ch)]
      (timbre/debug item)
      (recur))))
