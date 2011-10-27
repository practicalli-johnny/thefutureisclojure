(ns server
  (:require [clojure.data.json :as json]
            [clojure.main :as main]
            [clojure.pprint :as pprint])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler]
           [org.webbitserver.handler StaticFileHandler]))

(defn on-message [socket json]
  (let [msg (str "(ns repl) " (json/read-json json))]
    (try
      (let [result (load-string msg)]
        (println result)
        (.send socket (json/json-str
                       {:result (with-out-str (pprint/pprint result))})))
      (catch Exception e
        (.send socket (json/json-str
                       {:error (.getMessage (main/repl-exception e))}))))))

(defn start []
  (doto (WebServers/createWebServer 8000)
    (.add "/repl"
          (proxy [WebSocketHandler] []
            (onOpen [c] (println "opened" c))
            (onClose [c] (println "closed" c))
            (onMessage [c j] (on-message c j))))
    (.add (StaticFileHandler. "resources/public"))
    (.start)))

(defn -main [& m]
  (start))

