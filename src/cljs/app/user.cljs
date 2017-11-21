(ns app.user
  (:require
   [app.timbre-html-log
    :refer [toggle-html-log]]
   [app.devconsole]))

;; User commands

(defn toggle-console []
  (toggle-html-log))
