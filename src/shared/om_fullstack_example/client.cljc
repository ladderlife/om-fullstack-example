(ns om-fullstack-example.client
  (:refer-clojure :exclude [read])
  (:require #?@(:cljs [[om.next :as om :refer-macros [defui]]
                       [om.dom :as dom]
                       [cljs.reader :refer [read-string]]
                       [goog.dom :as gdom]]
                :clj [[cellophane.next :as om :refer [defui]]
                      [cellophane.dom :as dom]]))
  #?(:cljs (:import [goog.net XhrIo])))

#?(:cljs (enable-console-print!))

(defui Friend
  static om/Ident
  (ident [this props]
    [:person/by-id (:db/id props)])
  static om/IQuery
  (query [this]
    [:db/id :user/name])
  Object
  (render [this]
    (let [{:keys [user/name]} (om/props this)]
      (dom/div nil name))))

(def friend (om/factory Friend))

(defui Person
  static om/Ident
  (ident [this props]
    [:person/by-id (:db/id props)])
  static om/IQuery
  (query [this]
    [:db/id :user/name {:user/friends (om/get-query Friend)}])
  Object
  (render [this]
    (let [{:keys [db/id user/name user/friends]} (om/props this)]
      (dom/div #js {:style #js {:backgroundColor "lightgrey"
                                :padding 10 :margin 5}}
        (dom/div #js {:style #js {:fontWeight "bold"}} name)
        (apply dom/div #js {:style #js {:padding "0 5px"}}
          (map friend friends))))))

(def person (om/factory Person))

#?(:cljs
   (defn listen-for-friending
     [this]
     (let [last-press (atom nil)
           {:keys [people]} (om/props this)
           keypress (fn [code]
                      (let [number (- code 49)
                            last-two [number @last-press]
                            valid-index? (partial contains? (set (range (count people))))]
                        (if (every? valid-index? last-two)
                          (let [ids (map (comp :db/id people) last-two)]
                            (reset! last-press nil)
                            (om/transact! this `[(friend/add {:id ~(first ids) :friend ~(last ids)})]))
                          (reset! last-press number))))]
       (.addEventListener js/document "keypress" (fn [e] (keypress (.-keyCode e)))))))

(defui People
  static om/IQuery
  (query [this]
    [{:people (om/get-query Person)}])
  Object
  #?(:cljs (componentDidMount [this] (listen-for-friending this)))
  (render [this]
    (let [{:keys [people]} (om/props this)]
      (dom/div nil
        (apply dom/div nil
          (map person people))))))

(defn root-query []
  (om/get-query People))

(defmulti read om/dispatch)

(defmethod read :people
  [{:keys [state query]} k _]
  (let [st @state]
    (if (contains? st :person/by-id)
      {:value (om/db->tree query (get st k) st)}
      {:remote true})))

(defmulti mutate om/dispatch)

(defn add-friend [state id friend]
  (letfn [(add* [friends ref]
            (as-> friends $
                  (cond-> $ (not (some #{ref} friends)) (conj ref))
                  (sort-by second $)
                  (vec $)))]
    (if-not (= id friend)
      (-> state
          (update-in [:person/by-id id :user/friends]
                     add* [:person/by-id friend])
          (update-in [:person/by-id friend :user/friends]
                     add* [:person/by-id id]))
      state)))

(defmethod mutate 'friend/add
  [{:keys [state] :as env} key {:keys [id friend] :as params}]
  {:remote true
   :action
   (fn [] (swap! state add-friend id friend))})

(defn make-parser
  []
  (om/parser {:read read :mutate mutate}))

(defn merge-state
  [state novelty]
  (merge-with merge state novelty))

(defn tree->db
  [tree]
  (om/tree->db People tree true))

#?(:cljs (defn send
           "SECURITY: alert! danger! use of `read-string`
           TODO: use transit instead of edn"
           [query cb]
           (let [payload (pr-str (:remote query))
                 xhr-cb (fn [_]
                          (this-as this
                            (let [res (read-string (.getResponseText this))]
                              (cb res query))))]
             (.send XhrIo "/api" xhr-cb "POST" payload))))

(defonce app-state (atom {}))

#?(:cljs
    (let [reconciler
          (om/reconciler {:state  app-state
                          :parser (make-parser)
                          :normalize true
                          :merge-tree (fn [old new-tree]
                                        (merge-state old (tree->db new-tree)))
                          :send   send})]
      (om/add-root!
        reconciler
        People (gdom/getElement "app"))))
