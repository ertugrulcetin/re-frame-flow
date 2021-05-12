(ns re-frame-flow.core
  (:require
    [clojure.set :as set]
    [cljs.reader :as reader]
    [goog.storage.Storage]
    [goog.storage.mechanism.HTML5LocalStorage]
    [reagent.core :as r]
    [reagent.dom :as rdom]
    [re-frame.core :as rf]
    [re-frame.cofx :as cofx]
    [re-frame.events :as events]
    [re-frame.fx :as fx]
    [re-frame.interceptor :refer [->interceptor get-effect get-coeffect]]
    [re-frame.registrar :as rg]
    [re-frame.std-interceptors :as std-interceptors :refer [fx-handler->interceptor]]
    [re-frame.trace :as trace :include-macros true]
    ["react-flow-renderer" :default ReactFlow :refer [Background Controls ReactFlowProvider]]
    ["dagre" :as dagre]))


(def state* (atom {}))
(def elements (r/atom {}))


(defn- get-deps [result]
  (cond-> #{}
    (:dispatch result)
    (conj (-> result :dispatch first))

    (:dispatch-n result)
    (set/union (set (map first (remove nil? (:dispatch-n result)))))

    (:dispatch-later result)
    (#(if (map? (:dispatch-later result))
        (conj % (-> result :dispatch-later :dispatch first))
        (set/union % (set (map (comp first :dispatch) (remove nil? (:dispatch-later result)))))))

    (-> result :http :on-success)
    (conj (-> result :http :on-success first))

    (-> result :http-xhrio :on-success)
    (conj (-> result :http-xhrio :on-success first))

    (-> result :http :on-failure)
    (conj (-> result :http :on-failure first))

    (-> result :http-xhrio :on-failure)
    (conj (-> result :http-xhrio :on-failure first))

    true
    (set/union (-> result
                 (dissoc :dispatch :dispatch-n :dispatch-later :http :http-xhrio)
                 (keys)
                 (set)))))


(defn- kw->str [id]
  (if (qualified-keyword? id)
    (str (namespace id) "/" (name id))
    (name id)))


(defn- id->node [id dispatch?]
  {:id (kw->str id)
   :style {:fontSize 14
           :fontFamily "monospace"
           :wordBreak "break-word"
           :width 200}
   :data {:label id
          :name (name id)
          :namespace (namespace id)
          :dispatch dispatch?}
   :sourcePosition "right"
   :targetPosition "left"
   :position {:x 0 :y 0}})


(defn- ids->edge [id1 id2]
  {:id (str "e-" (kw->str id1) "+" (kw->str id2))
   :source (kw->str id1)
   :target (kw->str id2)
   :animated true})


(defn- create-node-and-edges [handlers dispatches]
  (reduce-kv
    (fn [acc k v]
      (let [nodes (map #(id->node % (boolean (% dispatches))) (cons k v))
            edges (map ids->edge (repeat k) v)]
        (into acc (concat nodes edges))))
    []
    (merge handlers dispatches)))


(defn- get-id->node-map [{:keys [handlers dispatches kind->id->handler]}]
  (let [fx     (:fx kind->id->handler)
        events (:event kind->id->handler)]
    (reduce
      (fn [m e]
        (let [id    (keyword (:id e))
              color (cond
                      (id dispatches) "orange"
                      (id fx) "red"
                      (= :db-handler (:id (last (id events)))) "#336edc"
                      :else "green")]
          (assoc m (:id e) (-> e
                             (assoc-in [:style :color] color)
                             (assoc-in [:style :border] (str "1px solid " color))))))
      {}
      (create-node-and-edges handlers dispatches))))


(set!
  std-interceptors/fx-handler->interceptor
  (fn fx-handler->interceptor
    [id handler-fn]
    (->interceptor
      :id :fx-handler
      :before (fn fx-handler-before
                [context]
                (let [new-context
                      (trace/with-trace
                        {:op-type :event/handler
                         :operation (get-coeffect context :original-event)}
                        (let [{:keys [event] :as coeffects} (get-coeffect context)
                              result (handler-fn coeffects event)]
                          (let [result (dissoc result :db)
                                deps   (get-deps result)]
                            (swap! state* (fn [state id v]
                                            (let [state (update-in state [:handlers id] set/union v)]
                                              (assoc state :id->node-map (get-id->node-map
                                                                           {:handlers (:handlers state)
                                                                            :dispatches (:dispatches state)
                                                                            :kind->id->handler @rg/kind->id->handler}))))
                              id
                              deps))
                          (assoc context :effects result)))]
                  (trace/merge-trace!
                    {:tags {:effects (get-effect new-context)
                            :coeffects (get-coeffect context)}})
                  new-context)))))


(set!
  rf/reg-event-fx
  (fn reg-event-fx
    ([id handler]
     (reg-event-fx id nil handler))
    ([id interceptors handler]
     (events/register id [cofx/inject-db
                          fx/do-fx
                          std-interceptors/inject-global-interceptors
                          interceptors
                          ;; due to hot reload warning...
                          ((resolve 'fx-handler->interceptor) id handler)]))))


(defn clear-cache! []
  (reset! state* {})
  (reset! elements {}))

;;--------------------------------------- View component ---------------------------------------

(def Graph (.. dagre -graphlib -Graph))
(def dagre-graph (Graph.))
(.setDefaultEdgeLabel dagre-graph #(clj->js {}))
(.setGraph dagre-graph (clj->js {:rankdir "LR"}))


(def react-flow-pro (r/adapt-react-class ReactFlowProvider))
(def react-flow (r/adapt-react-class ReactFlow))
(def background (r/adapt-react-class Background))
(def controls (r/adapt-react-class Controls))

(def storage (goog.storage.Storage. (goog.storage.mechanism.HTML5LocalStorage.)))
(def safe-prefix "ertu.re-frame-flow.")


(defn- safe-key [key]
  (str safe-prefix key))


(defn load
  ([key]
   (load key nil))
  ([key not-found]
   (let [value (.get storage (safe-key key))]
     (if (undefined? value)
       not-found
       (reader/read-string value)))))


(defn save! [key value]
  (.set storage (safe-key key) (pr-str value)))


(defonce show-panel? (r/atom false))
(defonce show-dispatches? (r/atom (load "show-dispatches?")))


(defn- update-handles-color []
  (let [css   ".react-flow__handle { background: white !important;
                                     border: 1px solid #b1b1b7 !important;}"
        head  (or (.-head js/document)
                (aget (js/document.getElementsByTagName "head") 0))
        style (js/document.createElement "style")]
    (.appendChild head style)
    (.appendChild style (js/document.createTextNode css))))


(defn- on-node-mouse-enter [elements hovered-node-id _ node]
  (let [id        (.-id node)
        ns*       ^String (.-data.namespace node)
        name*     ^String (.-data.name node)
        kw-prefix (if ^String (.-data.dispatch node) "" ":")]
    (reset! hovered-node-id id)
    (swap! elements
      (fn [elements* id v]
        (-> elements*
          (assoc-in [id :data :label] v)
          (assoc-in [id :style :zIndex] 4)))
      id
      (if ns*
        (str kw-prefix ns* "/" name*)
        (str kw-prefix name*)))))


(defn- on-node-mouse-leave [elements hovered-node-id _ node]
  (let [id    (.-id node)
        name* ^String (.-data.name node)]
    (reset! hovered-node-id nil)
    (swap! elements
      (fn [elements* id v]
        (-> elements*
          (assoc-in [id :data :label] v)
          (assoc-in [id :style :zIndex] 3)))
      id
      name*)))


(defn- update-nodes-positions [elements]
  (let [width     280
        height    36
        elements* (vals (:id->node-map @state*))
        elements* (if @show-dispatches?
                    elements*
                    (let [dispatch-node-ids (->> elements* (filter (comp :dispatch :data)) (map :id) (set))]
                      (remove #(or (-> % :data :dispatch)
                                   (some-> % :source kw->str dispatch-node-ids)) elements*)))
        _         (doseq [el elements*]
                    (if (:data el)
                      (.setNode dagre-graph (:id el) (clj->js {:width width :height height}))
                      (.setEdge dagre-graph (:source el) (:target el))))
        _         (.layout dagre dagre-graph)
        elements* (mapv
                    (fn [el]
                      (if (:data el)
                        (let [node-with-pos (.node dagre-graph (:id el))]
                          (assoc el :position {:x (+ (- (.-x node-with-pos)
                                                       (/ width 2))
                                                    (/ (js/Math.random) 1000))
                                               :y (- (.-y node-with-pos) (/ height 2))}))
                        el))
                    elements*)]
    (reset! elements (into {} (map #(vector (:id %) %) elements*)))))


(defn- traverse-path
  ([m id]
   (traverse-path m #{} id))
  ([m state id]
   (let [childs (id m)]
     (if (state id)
       []
       (if (seq childs)
         (let [state (conj state id)]
           (cons id (mapcat #(traverse-path m state %) childs)))
         [id])))))


(defn- get-nested-path [hovered-node-id elements]
  (let [m       (merge (:handlers @state*) (:dispatches @state*))
        sources (->> hovered-node-id (keyword) (traverse-path m) (map kw->str) (set))]
    (filter #(or (:data %) (sources (:source %))) elements)))


(defn- get-nodes [elements]
  (or (vals @elements) []))


(defn- flow-panel []
  (let [handle-keys      (fn [e]
                           (let [tag-name        (.-tagName (.-target e))
                                 entering-input? (contains? #{"INPUT" "SELECT" "TEXTAREA"} tag-name)]
                             (when (and (not entering-input?)
                                     (= (.-key e) "g")
                                     (.-ctrlKey e))
                               (swap! show-panel? not)
                               (.preventDefault e))))
        hovered-node-id  (r/atom nil)
        prev-fx-handlers (atom nil)]
    (r/create-class
      {:display-name "Flow Panel"
       :component-did-mount (fn []
                              (js/window.addEventListener "keydown" handle-keys)
                              (update-handles-color))
       :component-will-unmount (fn []
                                 (js/window.removeEventListener "keydown" handle-keys))
       :component-did-update (fn []
                               (when (or (nil? @prev-fx-handlers)
                                       (not= @prev-fx-handlers (:handlers @state*)))
                                 (reset! prev-fx-handlers (:handlers @state*))
                                 (update-nodes-positions elements)))
       :reagent-render (fn []
                         [react-flow-pro
                          [react-flow
                           {:on-node-mouse-enter (partial on-node-mouse-enter elements hovered-node-id)
                            :on-node-mouse-leave (partial on-node-mouse-leave elements hovered-node-id)
                            :default-position [10 10]
                            :style {:width "100%"
                                    :height "100vh"
                                    :position "absolute"
                                    :top "0"
                                    :left "0"
                                    :background "white"
                                    :opacity (if @show-panel? "9999" "0")
                                    :z-index (if @show-panel? "9999" "0")
                                    :visibility (if @show-panel? "visible" "hidden")}
                            :snap-to-grid true
                            :snap-grid [15 15]
                            :elements (if @hovered-node-id
                                        (get-nested-path @hovered-node-id (get-nodes elements))
                                        (get-nodes elements))}
                           [controls]
                           [:button
                            {:style {:bottom "0"
                                     :position "absolute"
                                     :margin-left "48px"
                                     :margin-bottom "12px"
                                     :z-index "99999"
                                     :border "1px solid grey"
                                     :border-radius "2px"
                                     :font "400 14px Arial"
                                     :padding "2px 6px"}
                             :on-click (fn [_]
                                         (save! "show-dispatches?" (swap! show-dispatches? not))
                                         (update-nodes-positions elements))}
                            (if @show-dispatches?
                              "Hide dispatches"
                              "Show dispatches")]
                           [background
                            {:color "#aaa"}]]])})))


(defn- panel-div []
  (let [id    "--re-frame-flow--"
        panel (js/document.getElementById id)]
    (if panel
      panel
      (let [new-panel (js/document.createElement "div")]
        (.setAttribute new-panel "id" id)
        (.appendChild (.-body js/document) new-panel)
        (js/window.focus new-panel)
        new-panel))))


(defn init! []
  (rdom/render [flow-panel] (panel-div)))
