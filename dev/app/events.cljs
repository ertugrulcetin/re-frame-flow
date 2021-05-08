(ns app.events
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx]]
   [app.db :as db]))

(reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(reg-fx
  ::my-effect
  (fn [_] "effect!"))

(reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-fx
  ::my-event
  (fn [{:keys [db]} [_ arg]]
    (case arg
      1 {:db db
         :dispatch [::call-some-stuff]}
      2 {:db db
         :dispatch-n [[::new-event]
                      [:kek]]}
      {:db db})))

(reg-event-fx
  ::call-some-stuff
  (fn [{:keys [db]} _]
    {:dispatch-n [[::do-call]
                  [::do-it]]}))

(reg-event-fx
  ::do-it
  (fn [{:keys [db]} _]
    {:db db
     :dispatch-later [{:ms 1
                       :dispatch [::cool-stuff]}]}))

(reg-event-fx
  ::do-call
  (fn [{:keys [db]} _]
    {:db db
     ::my-effect nil
     :http {:api-path "https://jsonplaceholder.typicode.com/posts"
            :method :get
            :on-success [::success]
            :on-failure [::fail]}}))

(reg-event-fx
  ::cool-stuff
  (fn [{:keys [db]} _]
    {:db db
     :dispatch [::call-db]}))

(reg-event-fx
  ::success
  (fn [{:keys [db]} _]
    {:db db}))

(reg-event-fx
  ::fail
  (fn [{:keys [db]} _]
    {:db db}))

(reg-event-db
  ::call-db
  (fn [db _]
    db))

(reg-event-db
  ::new-event
  (fn [db _]
    db))

(reg-event-fx
  :kek
  (fn [{:keys [db]} _]
    {:db db}))
