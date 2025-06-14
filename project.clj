(defproject io.github.fourteatoo/clj-blink "0.1.0-SNAPSHOT"
  :description "A simple and unofficial Blink Cameras API
  See https://github.com/MattTW/BlinkMonitorProtocol for protocol
  information."
  :url "http://github.com/fourteatoo/clj-blink"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.1"]
                 [clojure.java-time "1.4.3"]
                 [clj-http "3.13.0"]
                 [cheshire "6.0.0"]
                 [camel-snake-kebab "0.4.3"]]
  :profiles {:dev {:plugins [[lein-codox "0.10.8"]]}}
  :repl-options {:init-ns fourteatoo.clj-blink.api})
