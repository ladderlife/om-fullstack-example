(ns om-fullstack-tests.generative-test
  (:refer-clojure :exclude [read])
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [om-fullstack-example.client :as client :refer [mutate read]]
            [cellophane.next :as om :refer [defui]]))

(comment
  (def init-data
   {:people       [[:person/by-id 0]
                   [:person/by-id 1]
                   [:person/by-id 2]]
    :person/by-id {0 {:id 0 :name "Bob" :friends []}
                   1 {:id 1 :name "Laura" :friends []}
                   2 {:id 2 :name "Mary" :friends []}}})

  (def init-state {})

  (def root-query (om/get-query om_fullstack_example.client.People))

  (defn tree->db [component data _] data)

  (def gen-tx-add-remove
    (gen/vector
      (gen/fmap seq
                (gen/tuple
                  (gen/elements '[friend/add friend/remove])
                  (gen/fmap (fn [[n m]] {:id n :friend m})
                            (gen/tuple (gen/elements [0 1 2])
                                       (gen/elements [0 1 2])))))))

  (defn self-friended? [{:keys [id friends]}]
    (boolean (some #{id} (map :id friends))))

  (defn prop-no-self-friending []
    (prop/for-all [tx gen-tx-add-remove]
                  (let [parser (om/parser {:read read :mutate mutate})
                        state (atom init-state)]
                    (parser {:state state} tx)
                    (let [ui (parser {:state state} root-query)]
                      (not (some self-friended? (:people ui)))))))

  (defn friends-consistent?
    [people]
    (let [indexed (zipmap (map :id people) people)]
      (letfn [(consistent? [[id {:keys [friends]}]]
                (let [xs (map (comp :friends indexed :id) friends)]
                  (every? #(some #{id} (map :id %)) xs)))]
        (every? consistent? indexed))))

  (defn prop-friend-consistency
    []
    (prop/for-all [tx gen-tx-add-remove]
                  (let [parser (om/parser {:read read :mutate mutate})
                        state (atom init-state)]
                    (parser {:state state} tx) (let [ui (parser {:state state} root-query)]
                                                 (friends-consistent? (:people ui)))))))