(ns om-fullstack-example.server
  (:require
    [ring.middleware.resource :refer [wrap-resource]]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.adapter.jetty :refer [run-jetty]]
    [figwheel-sidecar.system :as sys]
    [com.stuartsierra.component :as component]
    [om-fullstack-example.api :as api])
  (:gen-class))

(defn om-handler
  "SECURITY HACK don't read-string!"
  [env {:keys [uri body]}]
  (when (re-matches #".*api$" uri)
    (let [payload (read-string (slurp body))]
      {:status 200
       :body   (pr-str (api/parse (dissoc env :datomic) payload))})))

(defn index->root [next]
  (fn [{:keys [uri] :as request}]
    (next
      (if (= uri "/")
        (assoc request :uri "/index.html")
        request))))

(defn make-handler
  [env]
  (-> (fn [request]
        (om-handler env request))
      (wrap-resource "public")
      (wrap-content-type)
      (index->root)))

(defrecord WebServer [port]
  component/Lifecycle
  (start [component]
    (let [env (api/hoist-conn (:api component))
          req-handler (make-handler env)
          container (run-jetty req-handler {:port port :join? false})]
      (assoc component :container container)))
  (stop [component]
    (.stop (:container component))))

(defn dev-system []
  (component/system-map
   :api (api/dev-system)
   :figwheel (sys/figwheel-system (sys/fetch-config))
   :webserver (component/using
                (WebServer. 3000)
                [:api])))

(defn start
  []
  (component/start (dev-system)))

(defn -main
  []
  (start))

(comment
  (def s (start))
  (component/stop s))