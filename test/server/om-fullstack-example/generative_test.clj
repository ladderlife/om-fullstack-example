(ns om-fullstack-tests.generative-test
  (:refer-clojure :exclude [read])
  (:require
    [clojure.test.check :as tc]
    [clojure.test.check.clojure-test :refer [defspec]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [om-fullstack-example.client :as client :refer [mutate read]]
    [cellophane.next :as om :refer [defui]]
    [clojure.test :refer [deftest is]]
    [om-fullstack-example.driver :refer [drive]]
    [cellophane.next :as om]))

(defn no-self-friending?
  [{:keys [people]}]
  (letfn [(self-friend? [{:keys [db/id user/friends]}]
            (contains? (set (map :db/id friends)) id))]
    (not (some self-friend? people))))

(defn friends-consistent?
  [{:keys [people]}]
  (let [indexed (zipmap (map :id people) people)]
    (letfn [(consistent? [[id {:keys [friends]}]]
              (let [xs (map (comp :friends indexed :id) friends)]
                (every? #(some #{id} (map :id %)) xs)))]
      (every? consistent? indexed))))

(def gen-tx-add-remove
  (gen/vector
    (gen/fmap seq
              (gen/tuple
                (gen/elements '[friend/add])
                (gen/fmap (fn [[n m]] {:id n :friend m})
                          (let [ids (mapv om/tempid [0 1 2])]
                            (gen/tuple (gen/elements ids)
                                       (gen/elements ids))))))))

(defn test-pred
  [predicate tx]
  (let [{:keys [pending-tree final-tree refresh-tree]} (drive tx)]
    (every? predicate [pending-tree final-tree refresh-tree])))

(def prop-no-self-friending
  (prop/for-all [tx gen-tx-add-remove] (test-pred no-self-friending? tx)))

(def prop-friend-consistency
  (prop/for-all [tx gen-tx-add-remove] (test-pred friends-consistent? tx)))

(comment
  (tc/quick-check 10 prop-friend-consistency)
  (tc/quick-check 10 prop-no-self-friending))
