(ns om-fullstack-example.util
  (:require [datomic.api :as d]))

;; stolen from https://github.com/daemianmack

(def expand-attrs
  {:one  [:db/cardinality :db.cardinality/one]
   :many [:db/cardinality :db.cardinality/many]

   :ref     [:db/valueType :db.type/ref]
   :keyword [:db/valueType :db.type/keyword]
   :long    [:db/valueType :db.type/long]
   :double  [:db/valueType :db.type/double]
   :float   [:db/valueType :db.type/float]
   :inst    [:db/valueType :db.type/instant]
   :bool    [:db/valueType :db.type/boolean]
   :str     [:db/valueType :db.type/string]
   :uuid    [:db/valueType :db.type/uuid]

   :uniq-v [:db/unique :db.unique/value]
   :uniq-i [:db/unique :db.unique/identity]

   :index [:db/index       true]
   :comp  [:db/isComponent true]})

(defn expand-abbreviated-attrs [[id attrs]]
  "Given a map of abbreviated attribute forms, expand into vanilla Datomic attribute datoms.

   Abbreviated syntax: {the-db-ident
                         [abbreviated-attr-keywords
                          optional-doc-string
                          optional-map-of-literal-attribute-pairs]}

   Given input of....
     {:user/foobar-count [:one :long \"The user's foobar count\" {:db/custom-attr true}]

   expand into...
     {:db/id          {:part :db.part/db :idx -1002942}
      :db/ident       :user/foobar-count
      :db/cardinality :db.cardinality/one
      :db/valueType   :db.type/long
      :db/doc         \"The user's foobar count\"
      :db/custom-attr true
      :db.install/_attribute :db.part/db}"
  (merge
    {:db/id                 (d/tempid :db.part/db)
     :db/ident              id
     :db.install/_attribute :db.part/db}
    (reduce
      (fn [m attr]
        (cond
          (map? attr)    (merge m attr)
          (string? attr) (assoc m :db/doc attr)
          :default       (do
                           (assert (expand-attrs attr) (pr-str attrs))
                           (apply assoc m (expand-attrs attr)))))
      {}
      attrs)))
