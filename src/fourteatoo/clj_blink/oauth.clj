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

(comment
  (def tokens {:access-token "eyJhbGciOiJSUzI1NiIsImprdSI6Ii9vYXV0aC9pbnRlcm5hbC9qd2tzIiwia2lkIjoiNjQ1NWZmNDEiLCJ0eXAiOiJKV1QifQ.eyJhcHBfaWQiOiJhbmRyb2lkIiwiY2lkIjoiYW5kcm9pZCIsImV4cCI6MTc2MDcyOTgxNCwiaGFyZHdhcmVfaWQiOiJjbGotYmxpbmsiLCJpYXQiOjE3NjA3MTU0MTQsImlzcyI6IkJsaW5rT2F1dGhTZXJ2aWNlLXByb2Q6dXMtZWFzdC0xOjU5OWIwMmU3Iiwib2lhdCI6MTc2MDcxNTQxNCwicm5kIjoiZG11XzYxYy1JZSIsInNjb3BlcyI6WyJjbGllbnQiXSwic2Vzc2lvbl9pZCI6IjJkMmZhNTc5LWUxMjItNDg0Ny1hZTNmLTE1MGUzODEwZTNmZiIsInVzZXJfaWQiOjEyNjkxMzQ3N30.VCBOu0flt7TvWsZbDT3jlJ3UvqqxfMnSo7vrtoMxJhiJnozit0xamCWu0vWBXkWcKKOowVHfRd5qsChiPahWp0_9AWgZHMYHQh8YSpQhoMaa0Qe2L7zeZx_PqJ0JurCgV85XS6IfgowjgjgQOO6IU8BDOVaYEJJuB7NtIVq5BfqnFGGHUBaq7CL1X9BAPAnMWw8U5jLQPO0prwfCLMhptU0fzgb1LBdaJiLxNf8sFKTzdcXuxoPAty7JyD1-EH1fpRYbwEuPdrP9JEKW1mKk7DUIIsuCeVIfarmoTwg_IRfI8-9NxJgHmrfyxTGNcc8bXXanAorn9lVn14igv6q_Og"
               :expires-in 14400
               :refresh-token "eyJhbGciOiJSUzI1NiIsImprdSI6Ii9vYXV0aC9pbnRlcm5hbC9qd2tzIiwia2lkIjoiNjQ1NWZmNDEiLCJ0eXAiOiJKV1QifQ.eyJpYXQiOjE3NjA3MTU0MTQsImlzcyI6IkJsaW5rT2F1dGhTZXJ2aWNlLXByb2Q6dXMtZWFzdC0xOjU5OWIwMmU3Iiwib2lhdCI6MTc2MDcxNTQxNCwicmVmcmVzaF9jaWQiOiJhbmRyb2lkIiwicmVmcmVzaF9zY29wZXMiOlsiY2xpZW50Il0sInJlZnJlc2hfdXNlcl9pZCI6MTI2OTEzNDc3LCJybmQiOiJDZ3hrTWxTUzA5Iiwic2Vzc2lvbl9pZCI6IjJkMmZhNTc5LWUxMjItNDg0Ny1hZTNmLTE1MGUzODEwZTNmZiIsInR5cGUiOiJyZWZyZXNoLXRva2VuIn0.C4mBd-TJlZqn6RHW-WwNFZNugSw1w4WgfCMS4nA-ZVEiqBiJQO10ouTmBjYpegrPUHc6NkTJQOf_MJ9oVwsrhZiM5jWEkq3SWuGl1gfizpIU9LSjZ1g7Ml0TyVaesMXw2jjqYW7HrbeVvAx2YDdUcKMbwNYxweuY_lU5GJey7k3RYidKGogcVC3Lh0AK-KPeaH4OBt54JHFxAKoBnVoA-AESBEr7fwmnLlKFWHxgwJF1IK8fT3zY-3BXLUKAmRuLlX8e3hhPqreIlRg3B2HeO12jBa-tdiwNjIBJzCToPZ2UEMjWbpcG89jaLahtNzL2DeDBpwxuziTuAfXasz8obw"
               :scope "client"
               :token-type "Bearer"}))

(comment
  (refresh-token "walter@pelissero.de" (:refresh-token tokens)))


;; :cached = nil
;; :request-time = 841
;; :repeatable? = false
;; :protocol-version = {:name "HTTP", :major 1, :minor 1}
;; :streaming? = true
;; :http-client = #object[org.apache.http.impl.client.InternalHttpClient 0x47a6e62f "org.apache.http.impl.client.InternalHttpClient@47a6e62f"]
;; :chunked? = false
;; :type = :clj-http.client/unexceptional-status
;; :reason-phrase = "Precondition Failed"
;; :headers = {"Server" "cloudflare",
;;             "Content-Type" "application/json",
;;             "Content-Length" "66",
;;             "strict-transport-security" "max-age=31536000; includeSubDomains",
;;             "Connection" "close",
;;             ...}
;; :orig-content-encoding = nil
;; :status = 412
;; :length = 66
;; :body = "{\"next_time_in_secs\":60,\"phone\":\" 49xxxxxxxx53\",\"tsv_state\":\"sms\"}"
;; :trace-redirects = []
