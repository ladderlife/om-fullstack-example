(ns om-fullstack-tests.example-based-test
  (:require
    [clojure.test :refer [deftest is]]
    [om-fullstack-example.driver :refer [drive]]
    [cellophane.next :as om]))

(deftest add-a-friend
  (let [{:keys [final-tree refresh-tree]}
        (drive `[(friend/add {:id ~(om/tempid 1) :friend ~(om/tempid 2)})])]
    (is (=))))
