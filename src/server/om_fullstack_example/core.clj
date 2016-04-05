(ns om-fullstack-example.core
  (:require [cellophane.next :as om]
            [om-fullstack-example.client :as client]))

(defn foo []
  (om/get-query om_fullstack_example.client.Friend))

