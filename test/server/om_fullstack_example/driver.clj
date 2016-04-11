(ns om-fullstack-example.driver
  (:require
    [clojure.walk :as walk]
    [clojure.set :refer [map-invert]]
    [cellophane.next :as om]
    [om-fullstack-example.api :as api]
    [om-fullstack-example.client :as client]))

(defn next-local-state
  "Transition to next local state via mutations"
  [state query]
  (let [env {:state (atom state)}]
    ((client/make-parser) env query)
    (-> env :state deref)))

(defn state->tree
  "Parse query using state"
  ([state query]
   (state->tree state query nil))
  ([state query target]
   (apply
     (client/make-parser)
     (cond-> [{:state (atom state)} query]
       (some? target) (conj target)))))

(defn sync-to-new-state
  "Get remote query, sends to the server, merges in the server response"
  [env state query]
  (let [remote-query (state->tree state query :remote)
        remote-response (api/parse env remote-query)]
    (client/merge-state state (client/tree->db remote-response))))

(defn transact
  "Applies mutations generating next state"
  [env state mutations reads]
  (let [start-tree (state->tree state reads)
        optimistic-state (next-local-state state mutations)
        optimistic-tree (state->tree optimistic-state reads)
        final-state (sync-to-new-state env optimistic-state (vec (concat mutations reads)))
        final-tree (state->tree final-state reads)
        refresh-tree (state->tree (sync-to-new-state env {} reads) reads)]
    (with-meta
      final-state
      {:start-tree start-tree
       :optimistic-tree optimistic-tree
       :final-tree final-tree
       :refresh-tree refresh-tree
       :emails-sent (-> env :email :emails-sent deref)})))

(defn drive*
  [server-system start-state full-client-query mutations]
  (reduce
    (fn [prev-state mutations]
      (transact server-system prev-state mutations full-client-query))
    start-state
    mutations))

(def id? "HACK" integer?)

(defn create-tempid-mapping
  "HACK"
  [all-ids mutations]
  (let [temp-ids (filter id? (set (flatten (map (comp vals second) mutations))))
        _ (assert (<= (count temp-ids) (count all-ids)))
        temp-ids (concat temp-ids (filter #(not (contains? (set temp-ids) %)) (range 1 10)))]
    (zipmap temp-ids all-ids)))

(defn replace-ids
  [mapping data]
  (walk/postwalk (fn [e] (if (id? e) (mapping e) e)) data))

(defn drive
  [mutations]
  (let [env (api/running-system :test)
        temp->id (create-tempid-mapping (api/all-ids env) mutations)
        mutations-grouped (vec (cons [] (map (partial conj []) (replace-ids temp->id mutations))))
        full-client-query (client/root-query)]
    (replace-ids (map-invert temp->id)
                 (meta (drive* env {} full-client-query mutations-grouped)))))