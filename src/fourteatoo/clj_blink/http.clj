(ns fourteatoo.clj-blink.http
  "Various fairly low level HTTP primitives and wrappers of `clj-http`."
  {:no-doc true}
  (:require [clojure.string :as s]
            [clj-http.client :as http]
            clj-http.cookies
            [cheshire.core :as json]
            [camel-snake-kebab.core :as csk]))

(def make-cookie-store
  clj-http.cookies/cookie-store)

(defonce ^:private default-cookie-jar (make-cookie-store))

(defn- keywordify-map [m]
  (->> (map (juxt (comp keyword s/lower-case name key) val) m)
       (into {})))

(defn- get-header [response header]
  (get (keywordify-map (:headers response)) header))

(defn- get-content-type [response]
  (let [ct (get-header response :content-type)]
    (when ct
      (or (s/split ct #";")))))

(defn- json-content? [response]
  (let [ct (first (get-content-type response))]
    (if ct
      (boolean (re-find #"json" ct))
      false)))

(defn- ->json-string [thing]
  (if (map? thing)
    (json/generate-string thing {:key-fn csk/->snake_case_string})
    thing))

(defn- stringify-body [opts]
  (update opts :body ->json-string))

(defn- kebab-keys [thing]
  (if (string? thing)
    (csk/->kebab-case-keyword thing)
    thing))

(defn- decode-json-reply [response]
  (if (json-content? response)
    (if (string? (:body response))
      (assoc response :json
             (-> response
                 :body
                 (json/parse-string kebab-keys)))
      (assoc response :json (:body response)))
    response))

(defn- restify [action]
  (fn [url & [opts]]
    (let [add-url #(assoc % :url url)]
      (-> (try
            (action (str url)
                    (merge {:cookie-store default-cookie-jar}
                           (stringify-body opts)))
            (catch Exception e
              (throw
               (ex-info "HTTP op exception"
                        {:op action :url url :opts opts
                         :http-status (:status (ex-data e))}
                        e))))
          add-url
          decode-json-reply))))

(def http-get (restify #'http/get))
(def http-post (restify #'http/post))
(def http-put (restify #'http/put))

(defn merge-http-opts [opts1 opts2]
  (merge-with (fn [o1 o2]
                (if (map? o1)
                  (merge o1 o2)
                  o2))
              opts1 opts2))
