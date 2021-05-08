(ns app.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [app.events :as events]
   [app.views :as views]
   [app.config :as config]
   [re-frame-flow.core :as re-flow]))


(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (re-flow/clear-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (re-frame/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (mount-root))
