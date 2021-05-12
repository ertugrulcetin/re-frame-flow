(ns re-frame-flow.macros
  #?(:cljs (:require-macros [net.cgrand.macrovich :as macros]))
  (:require
    #?(:clj [net.cgrand.macrovich :as macros])
    [re-frame.core :as rf]
    [clojure.set :as set]))

#?(:cljs (goog-define dispatch-enabled? false)
   :clj  (def ^boolean dispatch-enabled? false))

(defn ^boolean dispatch-trace-enabled? []
  dispatch-enabled?)

(macros/deftime
  (defmacro dispatch
    [event]
    (let [ns' *ns*]
      `(do
         (when (dispatch-trace-enabled?)
             (swap! re-frame-flow.core/state* update-in [:dispatches ~(keyword (str ns'))] set/union #{~(first event)}))
         (rf/dispatch ~event))))

  (defmacro dispatch-sync
    [event]
    (let [ns' *ns*]
      `(do
         (when (dispatch-trace-enabled?)
           (swap! re-frame-flow.core/state* update-in [:dispatches ~(keyword (str ns'))] set/union #{~(first event)}))
         (rf/dispatch-sync ~event)))))
