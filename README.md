# Re-frame Flow

**re-frame-flow** is a graph based visualization tool for re-frame event chains. Let's assume we clicked a login button and triggered a series of events. `login-fx -> http-fx -> some-fx -> some-db-handler ...` (event after event), so a path gets generated, re-frame-flow visualizes all paths in a graph.

![Re-frame Flow](imgs/re-frame-flow-example.png)


## Installation

- Add re-frame-flow to **dev** dependencies:
```clojure
:profiles
   {:dev
      {:dependencies [[org.clojars.ertucetin/re-frame-flow "X.Y.Z"]] }}
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
- Once it is loaded, trigger some events (Flow panel gets updated when an event is triggered).
- Focus the document window and press **ctrl-g** to open the flow panel.

## Running the example
- `git clone git@github.com:ertugrulcetin/re-frame-flow.git`
- `npm install`
- `lein watch`
- Open http://localhost:8280
- Click `Trigger 1` button
- Press `ctrl-g` (opens the panel)
- Pres it again (closes the panel)
- Click `Trigger 2` button
- Open the panel again (you will notice new events)
