(ns app.views
  (:require
   [re-frame.core :refer [subscribe]]
   [app.events :as events]
   [app.subs :as subs])
  (:require-macros [re-frame-flow.macros :refer [dispatch]]))

(defn main-panel []
  (let [name (subscribe [::subs/name])]
    [:div
     [:h1
      "Hello from " @name]
     [:button
      {:on-click #(dispatch [::events/my-event 1])}
      "Trigger 1"]
     [:button
      {:on-click #(dispatch [::events/my-event (+ 0 (- 3 1))])}
      "Trigger 2"]]))
