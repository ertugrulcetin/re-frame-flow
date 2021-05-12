(ns re-frame-flow.trace)

#?(:cljs (goog-define dispatch-enabled? false)
   :clj  (def ^boolean dispatch-enabled? false))

(defn ^boolean dispatch-trace-enabled? []
  dispatch-enabled?)
