(ns om-fullstack-example.api
  (:refer-clojure :exclude [read send])
  (:require
    [cellophane.next :as om]
    [com.stuartsierra.component :as component]
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
  [{:keys [conn email]} _ {:keys [id friend]}]
  {:action
   (fn []
     (when (not= id friend)
       (let [{:keys [tx-data]}
             @(d/transact
                conn
                [{:db/id id :user/friends friend}
                 {:db/id friend :user/friends id}])]
         (when (= 3 (count tx-data))
           (send email "You have a new friend!" friend id))))
     nil)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse
  [env query]
  (let [parser (om/parser {:read read :mutate mutate})]
    (parser env query)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Datomic
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defrecord Datomic [uri schema init-data]
  component/Lifecycle
  (start [component]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema)
      @(d/transact conn init-data)
      (assoc component :conn conn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Email
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol EmailSender
  (send [this subject to from]))

(defrecord SmtpSender [smtp-crendentials]
  EmailSender
  (send [this subject to from]))

(defrecord TestSender [emails-sent]
  EmailSender
  (send [this subject to from]
    (swap! emails-sent conj {:subject subject :to to :from from})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn test-system []
  (component/system-map
    :datomic (Datomic.
               (format "datomic:mem://%s" (d/squuid))
               (mapv util/expand-abbreviated-attrs schema)
               init-data)
    :email (TestSender. (atom []))))

(defn test-env []
  (let [sys (component/start (test-system))]
    (assoc sys :conn (get-in sys [:datomic :conn]))))
