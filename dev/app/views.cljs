(ns app.views
  (:require
    [re-frame-flow.macros :refer-macros [dispatch]]
    [re-frame.core :refer [subscribe]]
    [app.events :as events]))

(defn main-panel []
  [:div
   {:style {:font-size "20px"}}
   [:h1 "Welcome to Re-frame Flow demo"]
   [:p "Please follow the steps below:"]
   [:ul
    [:li
     [:button
      {:on-click #(dispatch [::events/my-event 1])
       :style {:font-size "18px"}}
      "Click here to trigger some events"]]
    [:br]
    [:li
     "Press " [:code {:style {:background "#c7c4c454"}} "ctrl-g"]]
    [:br]
    [:li
     [:button
      {:on-click #(dispatch [::events/my-event 2])
       :style {:font-size "18px"}}
      "Click here to trigger more events..."]]
    [:br]
    [:li
     "Press " [:code {:style {:background "#c7c4c454"}} "ctrl-g"] " again, and you will notice more events"]]])
