(ns server
  (:require [clojure.data.json :as json]
            [clojure.main :as main]
            [clojure.pprint :as pprint])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler]
           [org.webbitserver.handler StaticFileHandler]))

(defn on-message [socket json]
  (let [repl-ns (.data socket "clj-namespace")
        msg (str "(ns " repl-ns ")\n" (json/read-json json))]
    (try
      (let [result (load-string msg)]
        (.send socket (json/json-str
                       {:result (with-out-str (pprint/pprint result))
                        :ns repl-ns})))
      (catch Exception e
        (.send socket (json/json-str
                       {:error (.getMessage (main/repl-exception e))
                        :ns repl-ns}))))))

(defn start [port]
  (doto (WebServers/createWebServer port)
    (.add "/repl"
          (proxy [WebSocketHandler] []
            (onOpen [c]
              (let [repl-ns (str "repl.user" (.hashCode c))]
                (create-ns (symbol repl-ns))
                (.data c "clj-namespace" repl-ns)
                (println "opened" repl-ns)))
            (onClose [c]
              (let [repl-ns (.data c "clj-namespace")]
                (remove-ns (symbol repl-ns))
                (println "closed" repl-ns)))
            (onMessage [c j] (on-message c j))))
    (.add (StaticFileHandler. "resources/public"))
    (.start)))

(defn -main [& m]
  (let [port (Integer. (get (System/getenv) "PORT" "8000"))]
    (start port)))

