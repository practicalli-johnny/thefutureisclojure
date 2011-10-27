(ns server
  (:require [clojure.data.json :as json]
            [clojure.main :as main]
            [clojure.pprint :as pprint]
            [clojure.contrib.string :as string]
            [clojail.core :as clojail]
            clojail.testers)
  (:use clojure.test)
  (:import [org.webbitserver WebServer WebServers WebSocketHandler]
           [org.webbitserver.handler StaticFileHandler]))

(defn stream [s]
  (java.io.PushbackReader. (java.io.StringReader. s)))

(defn sandbox []
  (clojail/sandbox (disj clojail.testers/secure-tester 'def) :timeout 1000))

(with-test
  (defn ppr [el]
    (string/trim (with-out-str (pprint/pprint el))))
  (is (= "1337" (ppr 1337))))

(with-test
  (defn eval-sexp [sandbox sexp]
    (let [writer (java.io.StringWriter.)]
      (try
        (let [result (ppr (sandbox sexp {#'*out* writer}))]
          {:code (ppr sexp) :result result :out (str writer)})
        (catch Exception e
          {:code (ppr sexp) :error (.toString (main/repl-exception e))
           :out (str writer)}))))
  (is (= {:code "1337" :result "1337" :out ""} (eval-sexp (sandbox) 1337)))
  (is (= {:code "(+ 2 2)" :result "4" :out ""} (eval-sexp (sandbox) '(+ 2 2))))
  (is (= {:code "(println \"foo\")" :result "nil" :out "foo\n"}
         (eval-sexp (sandbox) '(println "foo"))))
  (is (string/substring? "No such namespace"
                         ((eval-sexp (sandbox) '(nonexistent/flarp)) :error)))
  (is (string/substring? "You tripped the alarm"
                         ((eval-sexp (sandbox) '(eval '(+ 2 2))) :error))))

(with-test
  (defn eval-stream [sandbox s]
    (loop [sexp (read s false nil)
           results []]
      (if (not (nil? sexp))
        (let [result (eval-sexp sandbox sexp)]
          (if (result :error)
            (conj results result)
            (recur (read s false nil) (conj results result))))
        results)))
  (is (= '({:code "(+ 2 2)" :result "4" :out ""}
           {:code "(- 3 2)" :result "1" :out ""}
           {:code "(errorboom/flarp)" :out ""
            :error "java.lang.RuntimeException: No such namespace: errorboom, compiling:(NO_SOURCE_PATH:0)"})
         (eval-stream (sandbox)
                      (stream "(+ 2 2) (- 3 2) (errorboom/flarp) (* 2 3)"))))
  (is (= '({:code "1337" :result "1337" :out ""})
         (eval-stream (sandbox) (stream "1337")))))

(defn on-connect [socket]
  (.data socket "clj-sandbox" (sandbox)))

(defn on-disconnect [socket]
  (let [sandbox (.data socket "clj-sandbox")]
    (remove-ns (ns-name (sandbox '*ns*)))))

(defn on-message [socket json]
  (let [repl-ns (.data socket "clj-namespace")
        sandbox (.data socket "clj-sandbox")
        msg (str (json/read-json json))
        reader (stream msg)]
    (let [results (eval-stream sandbox reader)]
      (.send socket (json/json-str {:ns repl-ns :eval results})))))

(defn start [port]
  (doto (WebServers/createWebServer port)
    (.add "/repl"
          (proxy [WebSocketHandler] []
            (onOpen [c] (on-connect c))
            (onClose [c] (on-disconnect c))
            (onMessage [c j] (on-message c j))))
    (.add (StaticFileHandler. "resources/public"))
    (.start)))

(defn -main [& m]
  (let [port (Integer. (get (System/getenv) "PORT" "8000"))]
    (start port)))

