(ns om-fullstack-example.client
  (:refer-clojure :exclude [read])
  (:require #?@(:cljs [[om.next :as om :refer-macros [defui]]
                       [om.dom :as dom]]
                :clj [[cellophane.next :as om :refer [defui]]
                      [cellophane.dom :as dom]])))

(defui Friend
  static om/Ident
  (ident [this props]
    [:person/by-id (:id props)])
  static om/IQuery
  (query [this]
    [:id :name])
  Object
  (render [this]
    (dom/div "hey")))

(defui Person
  static om/Ident
  (ident [this props]
    [:person/by-id (:id props)])
  static om/IQuery
  (query [this]
    [:id :name {:friends (om/get-query Friend)}]))

(defui People
  static om/IQuery
  (query [this]
    [{:people (om/get-query Person)}]))

(defmulti read om/dispatch)

(defmethod read :people
  [{:keys [state query] :as env} key _]
  (let [st @state]
    {:value (om/db->tree query (get st key) st)}))

(defmulti mutate om/dispatch)

(defn add-friend [state id friend]
  (letfn [(add* [friends ref]
            (cond-> friends
                    (not (some #{ref} friends)) (conj ref)))]
    (if-not (= id friend)
      (-> state
          (update-in [:person/by-id id :friends]
                     add* [:person/by-id friend])
          (update-in [:person/by-id friend :friends]
                     add* [:person/by-id id]))
      friend)))

(defmethod mutate 'friend/add
  [{:keys [state] :as env} key {:keys [id friend] :as params}]
  {:action
   (fn [] (swap! state add-friend id friend))})

(defn remove-friend [state id friend]
  (letfn [(remove* [friends ref]
            (cond->> friends
                     (some #{ref} friends) (into [] (remove #{ref}))))]
    (if-not (= id friend)
      (-> state
          (update-in [:person/by-id id :friends]
                     remove* [:person/by-id friend])
          (update-in [:person/by-id friend :friends]
                     remove* [:person/by-id id]))
      state)))

(defmethod mutate 'friend/remove
  [{:keys [state] :as env} key {:keys [id friend] :as params}]
  {:action (fn [] (swap! state remove-friend id friend))})
