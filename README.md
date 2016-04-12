# om-fullstack-example

### driver

Lets you apply arbitrary sequences of om.next mutations. For each invocation of `drive` creates a test system (email and database). For each mutation applies it locally, sends a query to the server and merges the response into local state.

This is very useful for fullstack property or example based tests against user facing concerns (ui data tree and emails sent).

```clj
(require '[om-fullstack-example.driver :refer [drive]])

(drive `[(friend/add {:id 1 :friend 2})])
=>
{:start-tree {:people [{:db/id 1, :user/name "Bob"} {:db/id 2, :user/name "Mary"} {:db/id 3, :user/name "Laura"}]},
 :optimistic-tree {:people [{:db/id 1, :user/name "Bob", :user/friends [{:db/id 2, :user/name "Mary"}]}
                            {:db/id 2, :user/name "Mary", :user/friends [{:db/id 1, :user/name "Bob"}]}
                            {:db/id 3, :user/name "Laura"}]},
 :final-tree {:people [{:db/id 1, :user/name "Bob", :user/friends [{:db/id 2, :user/name "Mary"}]}
                       {:db/id 2, :user/name "Mary", :user/friends [{:db/id 1, :user/name "Bob"}]}
                       {:db/id 3, :user/name "Laura"}]},
 :refresh-tree {:people [{:db/id 1, :user/name "Bob", :user/friends [{:db/id 2, :user/name "Mary"}]}
                         {:db/id 2, :user/name "Mary", :user/friends [{:db/id 1, :user/name "Bob"}]}
                         {:db/id 3, :user/name "Laura"}]},
 :emails-sent [{:subject "You have a new friend!", :to 2, :from 1}]}
```
