# Re-frame Flow

**re-frame-flow** is a graph based visualization tool for re-frame event chains.

![Re-frame Flow](imgs/re-frame-flow-example.png)


## Installation

- Add re-frame-flow to **dev** dependencies:
```clojure
:profiles
   {:dev
      {:dependencies [[re-frame-flow "X.Y.Z"]] }}
```

- Add to **preloads**:
```clojure
{...
 :preloads [re-frame-flow.preload]
 ...}
```

- Update **^:dev/after-load**:
```clojure
(ns my-app.core
  (:require [re-frame-flow.core :as re-flow]))

(defn ^:dev/after-load mount-root []
  (re-flow/clear-cache!)
  ...)
```

## Usage
- Make sure you have followed all of the installation instructions above.
- Start up your application.
- Once it is loaded, trigger some events (flow gets updated when an event is triggered).
- Focus the document window and press **ctrl-g** to open the flow panel.
