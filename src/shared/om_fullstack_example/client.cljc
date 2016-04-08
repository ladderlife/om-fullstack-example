(ns om-fullstack-example.client
  (:refer-clojure :exclude [read])
  (:require #?@(:cljs [[om.next :as om :refer-macros [defui]]
                       [om.dom :as dom]]
                :clj [[cellophane.next :as om :refer [defui]]
                      [cellophane.dom :as dom]])))

(defui Friend
  static om/Ident
  (ident [this props]
    [:person/by-id (:db/id props)])
  static om/IQuery
  (query [this]
    [:db/id :user/name]))

(defui Person
  static om/Ident
  (ident [this props]
    [:person/by-id (:db/id props)])
  static om/IQuery
  (query [this]
    [:db/id :user/name {:user/friends (om/get-query Friend)}]))

(defui People
  static om/IQuery
  (query [this]
    [{:people (om/get-query Person)}]))

(defn get-query []
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
            (cond-> friends
                    (not (some #{ref} friends)) (conj ref)))]
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
