(ns app.views
  (:require
    [re-frame-flow.macros :refer-macros [dispatch]]
    [re-frame.core :refer [subscribe]]
    [app.events :as events]
    [app.subs :as subs]))

(defn main-panel []
  (let [name (subscribe [::subs/name])]
    [:div
     [:h1
      "Hello from " @name]
     [:button
      {:on-click #(dispatch [::events/my-event 1])}
      "Trigger 1"]
     [:button
      {:on-click #(dispatch [::events/my-event 2])}
      "Trigger 2"]]))
