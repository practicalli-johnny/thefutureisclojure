(defproject clojure-prez "1.0.0-SNAPSHOT"
  :description "Clojure presentation"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojure/data.json "0.1.2"]
                 [org.webbitserver/webbit "0.2.10"]
                 [clojail "0.4.6-beta1"]
                 [com.miglayout/miglayout "3.7.4"]]
  :jvm-opts ["-Djava.security.policy=security.policy"]
  :main server)
