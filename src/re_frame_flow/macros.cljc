(ns re-frame-flow.macros
  #?(:cljs (:require-macros [net.cgrand.macrovich :as macros]))
  (:require
    #?(:clj [net.cgrand.macrovich :as macros])
    [re-frame.core :as rf]
    [re-frame-flow.trace :as trace]
    [clojure.set :as set]))

(macros/deftime
  (defmacro dispatch
    [event]
    (let [ns' *ns*]
      `(do
         (when (trace/dispatch-trace-enabled?)
           (swap! @(resolve 're-frame-flow.core/state*) update-in
             [:dispatches ~(keyword (str ns'))] set/union #{~(first event)}))
         (rf/dispatch ~event))))

  (defmacro dispatch-sync
    [event]
    (let [ns' *ns*]
      `(do
         (when (trace/dispatch-trace-enabled?)
           (swap! @(resolve 're-frame-flow.core/state*) update-in
             [:dispatches ~(keyword (str ns'))] set/union #{~(first event)}))
         (rf/dispatch-sync ~event)))))
