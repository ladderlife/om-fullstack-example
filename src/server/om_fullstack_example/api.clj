(ns om-fullstack-example.api
  (:refer-clojure :exclude [read])
  (:require
    [cellophane.next :as om]
    [datomic.api :as d]
    [om-fullstack-example.util :as util]))

(defmulti read om/dispatch)

(defn all-user-ids
  [db]
  (d/q '[:find [?e ...]
         :where
         [?e :user/name _]]
       db))

(defmethod read :people
  [{:keys [conn query]} _ _]
  (let [db (d/db conn)
        ids (all-user-ids db)]
    {:value (d/pull-many db query ids)}))


(defmulti mutate om/dispatch)

(defmethod mutate 'friend/add
  [{:keys [conn]} key {:keys [id friend]}]
  {:action
   (fn []
     @(d/transact conn [{:db/id id :user/friends friend}
                        #_{:db/id friend :user/friends id}])
     nil)})

;;; Public

; test system
; prod system

(defn parse
  [env query]
  (let [parser (om/parser {:read read :mutate mutate})]
    (parser env query)))

(defn all-ids
  "HACK for om-fullstack-example/driver"
  [{:keys [conn]}]
  (all-user-ids (d/db conn)))

(def schema
  {:user/name    [:one :str]
   :user/friends [:many :ref]})

(def init-data
  [{:db/id (d/tempid :db.part/user)
    :user/name "Bob"}
   {:db/id (d/tempid :db.part/user)
    :user/name "Mary"}
   {:db/id (d/tempid :db.part/user)
    :user/name "Laura"}])

(defn test-env []
  (let [uri (format "datomic:mem://%s" (d/squuid))
        _ (d/create-database uri)
        conn (d/connect uri)
        _ @(d/transact conn (map util/expand-abbreviated-attrs schema))
        _ @(d/transact conn init-data)]
    {:conn conn}))
