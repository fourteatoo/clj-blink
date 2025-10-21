(ns fourteatoo.clj-blink.oauth
  (:require [fourteatoo.clj-blink.http :as http]))

(def ^:dynamic *client-id* "android")
(def ^:dynamic *hardware-id* "clj-blink")
(def ^:dynamic *user-agent* "27.0ANDROID_28373244")

(def ^:dynamic *login-endpoint*
  "https://api.oauth.blink.com/oauth/token")

(defn default-headers []
  {:user-agent *user-agent*
   :hardware_id *hardware-id*})

(defn- do-client-registration [username password]
  (let [data {:username username
              :client_id *client-id*
              :scope "client"
              :grant_type "password"
              :password password}]
    (http/http-post *login-endpoint*
                    {:headers (assoc (default-headers)
                                     :content-type "application/x-www-form-urlencoded")
                     :unexceptional-status #(or (<= 200 % 299)
                                                (= 412 %))
                     :as :x-www-form-urlencoded
                     :form-params data})))

(defn- do-client-registration-with-verification-code [username password verification-code]
  (let [data (merge {:username username
                     :client_id *client-id*
                     :scope "client"
                     :grant_type "password"
                     :password password})]
    (http/http-post *login-endpoint*
                    {:headers (merge (default-headers)
                                     {:content-type "application/x-www-form-urlencoded"
                                      :2fa-code verification-code})
                     :unexceptional-status #(or (<= 200 % 299)
                                                (= 412 %))
                     :as :x-www-form-urlencoded
                     :form-params data})))

(defn refresh-token [username token]
  (let [data {:username username
              :client_id *client-id*
              :scope "client"
              :grant_type "refresh_token"
              :refresh_token token}]
    (:json
     (http/http-post *login-endpoint*
                     {:headers (assoc (default-headers)
                                      :content-type "application/x-www-form-urlencoded")
                      :unexceptional-status #(or (<= 200 % 299)
                                                 (= 412 %))
                      :as :x-www-form-urlencoded
                      :form-params data}))))

(defn register-client
  "Register a new client with the server.  This function may trigger the
  2FA from Blink.  You will be asked to enter a verification code that
  is delivered to you via email or SMS.  Return a map of the tokens
  that are to be used in the successive API calls."
  [username password]
  (let [reply (do-client-registration username password)]
    (:json
     (if (:access-token reply)
       reply
       (do
         (println "verification code sent to" (:phone reply))
         (println "Type here the verification code you have received:")
         (let [verification-code (read-line)]
           (do-client-registration-with-verification-code username password verification-code)))))))
