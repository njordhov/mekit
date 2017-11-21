(ns app.bridge
  (:require-macros
   [cljs.core.async.macros
    :refer [go go-loop alt!]])
  (:require
   [cljs.core.async :as async
    :refer [chan close! timeout put!]]
   [goog.net.XhrIo :as xhr] ;; ## TODO: eliminate
   [cljs-http.client :as http]))

(defn json-onto [url ch]
  (-> (http/get url {:with-credentials? false})
      (async/pipe ch)))

(defn open-resource
  ([endpoint n]
   (open-resource endpoint n 1))
  ([{:keys [url extract] :as endpoint} n buf & {:keys [concur] :or {concur n}}]
   {:pre [(string? url) (fn? extract)(int? n)]}
   (let [out> (chan buf (comp
                         (map extract)
                         (partition-all n)))
         in> (chan n)]
     ;; Preferable but cannot do yet due to bug in core.async:
     ;; http://dev.clojure.org/jira/browse/ASYNC-108
     ;; (async/to-chan (repeat url))
     (async/onto-chan in> (repeat url))
     (async/pipeline-async concur out> json-onto in>)
     out>)))
