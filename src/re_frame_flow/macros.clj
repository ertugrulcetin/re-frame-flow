(ns re-frame-flow.macros
  (:require
   [re-frame.core :as rf]
   [clojure.set :as set]))


(defmacro dispatch
  [event]
  (let [ns' *ns*]
    `(do
       (swap! re-frame-flow.core/state* update-in [:dispatches ~(keyword (str ns'))] set/union #{~(first event)})
       (rf/dispatch ~event))))


(defmacro dispatch-sync
  [event]
  (let [ns' *ns*]
    `(do
       (swap! re-frame-flow.core/state* update-in [:dispatches ~(keyword (str ns'))] set/union #{~(first event)})
       (rf/dispatch-sync ~event))))
