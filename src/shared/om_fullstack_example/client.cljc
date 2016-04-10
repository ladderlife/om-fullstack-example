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
      (dom/p nil name))))

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
      (dom/div nil
        (dom/p nil name)
        (apply dom/ul nil
          (map friend friends))))))

(def person (om/factory Person))

(defui People
  static om/IQuery
  (query [this]
    [{:people (om/get-query Person)}])
  Object
  (render [this]
    (let [{:keys [people]} (om/props this)]
      (dom/div nil
        (apply dom/ul nil
          (map person people))))))

(defn root-query []
  (om/get-query People))

(defmulti read om/dispatch)

(defmethod read :people
  [{:keys [state query]} _ _]
  ;; HACK
  (let [st @state
        query (mapv #(if (keyword? %) % (first (keys %))) query)]
    (if (contains? st :person/by-id)
      {:value (->> st :person/by-id vals
                (mapv #(select-keys % query)))}
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

(defn replace-friends
  [{:keys [user/friends] :as elem}]
  (update elem :user/friends
          #(mapv (fn [{:keys [db/id]}] [:person/by-id id]) %)))

(defn tree->db
  [tree]
  (if-let [people (:people tree)]
    {:person/by-id (into {} (map (juxt :db/id replace-friends) people))}
    tree))

(defn merge-state
  [state novelty]
  (merge-with merge state novelty))

#?(:cljs (defn send
           "SECURITY: alert! danger! use of `read-string`
           TODO: use transit instead of edn"
           [query cb]
           (let [payload (pr-str (:remote query))
                 xhr-cb (fn [_]
                          (this-as this
                            (let [res (read-string (.getResponseText this))]
                              (println "res" res)
                              (cb res query))))]
             (.send XhrIo "/api" xhr-cb "POST" payload))))

(defonce app-state (atom {}))

#?(:cljs
    (let [reconciler
          (om/reconciler {:state  app-state
                          :parser (om/parser {:read read :mutate mutate})
                          :merge-tree (fn [old new-tree]
                                        (merge-state old (tree->db new-tree)))
                          :send   send})]
      (om/add-root!
        reconciler
        People (gdom/getElement "app"))))
