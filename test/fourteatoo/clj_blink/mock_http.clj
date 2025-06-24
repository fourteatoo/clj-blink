(ns fourteatoo.clj-blink.mock-http
  (:require [fourteatoo.clj-blink.http :as http]
            [clojure.tools.trace :as trace]
            [clojure.test :as t]
            [cheshire.core :as json]))

(defn toggle-trace []
  (trace/trace-vars clj-http.client/get
                    clj-http.client/post
                    clj-http.client/put))

(defn any-http [url & [opts]]
  (let [opts (dissoc opts :cookie-store)]
    {:status 200
     :headers {:content-type "json"}
     :body (json/generate-string {:url url :opts opts})}))

(defn call-with-mocks [f]
  (with-redefs [clj-http.client/get any-http
                clj-http.client/put any-http
                clj-http.client/post any-http]
    (f)))
