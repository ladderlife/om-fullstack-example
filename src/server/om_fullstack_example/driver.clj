(ns om-fullstack-example.driver
  (:require
    [clojure.walk :as walk]
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
  "Applies mutations"
  [env state mutations reads]
  (let [pending-state (next-local-state state mutations)
        pending-tree (state->tree pending-state reads)
        final-state (sync-to-new-state env pending-state (vec (concat mutations reads)))
        final-tree (state->tree final-state reads)
        refresh-tree (state->tree (sync-to-new-state env {} reads) reads)]
    (with-meta
      final-state
      {:final-tree final-tree
       :refresh-tree refresh-tree
       :pending-tree pending-tree
       :emails-sent "TODO"})))

(defn drive*
  [env full-client-read-query mutations]
  (reduce
    (fn [elems mutation]
      (let [mutations (if mutation [mutation] [])]
        (conj
          elems
          (transact env (or (last elems) {})
                    mutations full-client-read-query))))
    [(sync-to-new-state env {} full-client-read-query)]
    (vec (cons nil mutations))))

(def id? "HACK" integer?)

(defn replace-tempids
  "HACK"
  [all-ids mutations]
  (let [temp-ids (filter id? (set (flatten (map (comp vals second) mutations))))
        _ (assert (<= (count temp-ids) (count all-ids)))
        temp->id (zipmap temp-ids all-ids)]
    (walk/postwalk (fn [e] (if (id? e) (temp->id e) e)) mutations)))

(defn drive
  [mutations]
  (let [env (api/test-env)
        mutations (replace-tempids (api/all-ids env) mutations)]
    (meta (last (drive* env (client/get-query) mutations)))))
