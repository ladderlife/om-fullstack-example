(ns om-fullstack-example.example-test
  (:require
    [om-fullstack-example.driver :refer [drive]]
    [clojure.test :refer [deftest is testing]]))

(deftest loading-data
  (let [{:keys [start-tree final-tree]} (drive [])]
    (testing "ui data tree starts empty"
      (is (= {} start-tree)))
    (testing "load data from server"
      (is (= {:people [{:db/id 1 :user/name "Bob"}
                       {:db/id 2 :user/name "Mary"}
                       {:db/id 3 :user/name "Laura"}]}
            final-tree)))))

(deftest adding-a-friend
  (let [{:keys [optimistic-tree final-tree refresh-tree emails-sent]}
        (drive `[(friend/add {:id 1 :friend 2})])]
    (testing "we add a friend optimistically"
      (is (= {:people [{:db/id 1 :user/name "Bob"
                        :user/friends [{:db/id 2 :user/name "Mary"}]}
                       {:db/id 2 :user/name "Mary"
                        :user/friends [{:db/id 1 :user/name "Bob"}]}
                       {:db/id 3 :user/name "Laura"}]}
             optimistic-tree final-tree refresh-tree)))
    (testing "we send a notification email"
      (is (= emails-sent [{:subject "You have a new friend!" :to 2 :from 1}])))))

(comment
  ; 1000 user actions in less than a second
  (time (do (drive (repeat 1000 `(friend/add {:id 1 :friend 2})))
            nil)))