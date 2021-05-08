(defproject re-frame-flow "0.1.0"
  :description "Graph based visualization tool for Re-frame event chains"

  :author "Ertuğrul Çetin"

  :url "https://github.com/ertugrulcetin/re-frame-flow"

  :license {:name "MIT License"
            :url  "https://opensource.org/licenses/MIT"}

  :dependencies [[thheller/shadow-cljs "2.12.5"]]

  :min-lein-version "2.9.0"

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :source-paths ["src"]

  :resource-paths ["resources" "target/cljsbuild"]

  :aliases {"watch" ["with-profile" "dev" "run" "-m" "shadow.cljs.devtools.cli" "watch" "app"]}

  :profiles {:dev {:dependencies [[reagent "1.0.0"]
                                  [re-frame "1.2.0"]]
                   :source-paths ["dev"]}})
