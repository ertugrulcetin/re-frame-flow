(ns re-frame-flow.core
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [re-frame.core :as rf]
   [re-frame.interceptor :refer [->interceptor get-effect get-coeffect]]
   [re-frame.events :as events]
   [re-frame.cofx :as cofx]
   [re-frame.fx :as fx]
   [re-frame.registrar :as rg]
   [re-frame.std-interceptors :as std-interceptors :refer [fx-handler->interceptor]]
   [re-frame.trace :as trace :include-macros true]
   [clojure.set :as set]
   ["react-flow-renderer" :default ReactFlow :refer [addEdge
                                                     Background
                                                     Controls
                                                     ReactFlowProvider]]
   ["dagre" :as dagre]))


(def fx-handlers (atom {}))
(def elements* (r/atom {}))


(defn- get-deps [result]
  (cond-> #{}
    (:dispatch result)
    (conj (-> result :dispatch first))

    (:dispatch-n result)
    (set/union (set (map first (:dispatch-n result))))

    (:dispatch-later result)
    (#(if (map? (:dispatch-later result))
        (conj % (-> result :dispatch-later :dispatch first))
        (set/union % (set (map (comp first :dispatch) (:dispatch-later result))))))

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


(defn- create-node-and-edges [handlers]
  (reduce-kv
    (fn [acc k v]
      (let [nodes (map (fn [id]
                         {:id (kw->str id)
                          :style {:fontSize 14
                                  :fontFamily "monospace"
                                  :wordBreak "break-word"
                                  :width 200}
                          :data {:label id
                                 :name (name id)
                                 :namespace (namespace id)}
                          :sourcePosition "right"
                          :targetPosition "left"
                          :position {:x 0 :y 0}})
                    (cons k v))
            edges (map (fn [id1 id2]
                         {:id (str "e-" (kw->str id1) "+" (kw->str id2))
                          :source (kw->str id1)
                          :target (kw->str id2)
                          :type "smoothstep"
                          :animated true}) (repeat k) v)]
        (into acc (concat nodes edges))))
    []
    handlers))


(defn- get-elements [handlers]
  (let [m @rg/kind->id->handler
        fx (:fx m)
        events (:event m)]
    (reduce
      (fn [m e]
        (let [id (keyword (:id e))
              color (cond
                      (id fx) "red"
                      (= :db-handler (:id (last (id events)))) "#336edc"
                      :else "green")]
          (assoc m (:id e) (-> e
                             (assoc-in [:style :color] color)
                             (assoc-in [:style :border] (str "1px solid " color))))))
      {}
      (create-node-and-edges handlers))))


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
                                deps (get-deps result)]
                            (swap! fx-handlers update-in [id] set/union deps)
                            (reset! elements* (get-elements @fx-handlers)))
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
  (reset! fx-handlers {})
  (reset! elements* {}))

;;--------------------------------------- View component ---------------------------------------

(def Graph (.. dagre -graphlib -Graph))
(def dagre-graph (Graph.))
(.setDefaultEdgeLabel dagre-graph #(clj->js {}))


(def react-flow-pro (r/adapt-react-class ReactFlowProvider))
(def react-flow (r/adapt-react-class ReactFlow))
(def background (r/adapt-react-class Background))
(def controls (r/adapt-react-class Controls))

(defonce show-panel? (r/atom false))

(defn- update-handles-color []
  (let [css ".react-flow__handle { background: white !important;
                                   border: 1px solid #b1b1b7 !important;}"
        head (or (.-head js/document)
               (aget (js/document.getElementsByTagName "head") 0))
        style (js/document.createElement "style")]
    (.appendChild head style)
    (.appendChild style (js/document.createTextNode css))))

(defn flow-panel []
  (let [handle-keys (fn [e]
                      (let [tag-name (.-tagName (.-target e))
                            entering-input? (contains? #{"INPUT" "SELECT" "TEXTAREA"} tag-name)]
                        (when (and (not entering-input?)
                                (= (.-key e) "g")
                                (.-ctrlKey e))
                          (swap! show-panel? not)
                          (.preventDefault e))))
        hovered-node-id (r/atom nil)]
    (r/create-class
      {:display-name "Flow Panel"
       :component-did-mount (fn []
                              (js/window.addEventListener "keydown" handle-keys)
                              (update-handles-color))
       :component-will-unmount (fn []
                                 (js/window.removeEventListener "keydown" handle-keys))
       :reagent-render (fn []
                         (let [width 280
                               height 36
                               _ (.setGraph dagre-graph (clj->js {:rankdir "LR"}))
                               elements (vals @elements*)
                               _ (doseq [el elements]
                                   (if (:data el)
                                     (.setNode dagre-graph (:id el) (clj->js {:width width :height height}))
                                     (.setEdge dagre-graph (:source el) (:target el))))
                               _ (.layout dagre dagre-graph)
                               elements (mapv
                                          (fn [el]
                                            (if (:data el)
                                              (let [node-with-pos (.node dagre-graph (:id el))]
                                                (assoc el :position {:x (+ (- (.-x node-with-pos)
                                                                             (/ width 2))
                                                                          (/ (js/Math.random) 1000))
                                                                     :y (- (.-y node-with-pos) (/ height 2))}))
                                              el))
                                          elements)]
                           [react-flow-pro
                            [react-flow
                             {:on-node-mouse-enter (fn [_ node]
                                                     (let [id (.-id node)
                                                           ns* ^String (.-data.namespace node)
                                                           name* ^String (.-data.name node)]
                                                       (reset! hovered-node-id id)
                                                       (swap! elements*
                                                         (fn [elements id v]
                                                           (-> elements
                                                             (assoc-in [id :data :label] v)
                                                             (assoc-in [id :style :zIndex] 4)))
                                                         id
                                                         (if ns*
                                                           (str ":" ns* "/" name*)
                                                           (str ":" name*)))))
                              :on-node-mouse-leave (fn [_ node]
                                                     (let [id (.-id node)
                                                           name* ^String (.-data.name node)]
                                                       (reset! hovered-node-id nil)
                                                       (swap! elements*
                                                         (fn [elements id v]
                                                           (-> elements
                                                             (assoc-in [id :data :label] v)
                                                             (assoc-in [id :style :zIndex] 3)))
                                                         id
                                                         name*)))
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
                                          (filter #(or (:data %) (= (:source %) @hovered-node-id)) elements)
                                          elements)}
                             [controls]
                             [background
                              {:color "#aaa"
                               :gap 16}]]]))})))


(defn panel-div []
  (let [id "--re-frame-flow--"
        panel (js/document.getElementById id)]
    (if panel
      panel
      (let [new-panel (js/document.createElement "div")]
        (.setAttribute new-panel "id" id)
        (.appendChild (.-body js/document) new-panel)
        (js/window.focus new-panel)
        new-panel))))


(defn init! []
  (clear-cache!)
  (rdom/render [flow-panel] (panel-div)))
