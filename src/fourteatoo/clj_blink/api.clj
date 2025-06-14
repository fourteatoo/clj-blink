(ns fourteatoo.clj-blink.api
  "The main API interface.  Call `register-client` or
  `authenticate-client` and, with the returned client map, you can use
  the other primitives."
  (:require
   [fourteatoo.clj-blink.http :as http]
   [java-time :as jt]))

(def ^:private blink-domain "immedia-semi.com")

(defn- blink-url [& [tier]]
  (str "https://rest-" (or tier "prod") "." blink-domain))

(defn- make-uuid []
  (java.util.UUID/randomUUID))

(def ^:dynamic *device-id* "clj-blink")

(defn- default-login-data []
  {:device-identifier *device-id*
   :client-name *device-id*})

(defrecord BlinkClient [email password unique-id
                   account-id client-id auth-token tier])

(defn- make-headers [client]
  (cond-> {:content-type "application/json"
           :user-agent "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:138.0) Gecko/20100101 Firefox/138.0"}
    (:auth-token client) (assoc "TOKEN_AUTH" @(:auth-token client))))

(defn- add-headers [client opts]
  (update opts :headers merge (make-headers client)))

(defn- unauthenticated-exception? [e]
  (= 401 (:http-status (ex-data e))))

(def refresh-client-registration)

(defn- update-auth-token! [client]
  (let [reply (refresh-client-registration client)]
    (reset! (:auth-token client) (get-in reply [:auth :token])))
  client)

(defn- with-headers [op]
  (fn [client url & [opts & rest]]
    (apply op client url (cons (add-headers client opts) rest))))

(defn- with-auto-reauth [op]
  (fn [client url & rest]
    (let [exec #(apply op client url rest)]
      (try
        (exec)
        (catch Exception e
          (if (unauthenticated-exception? e)
            (do
              (update-auth-token! client)
              (exec))
            (throw e)))))))

(defn- with-original-http-op [op]
  (fn [client url & rest]
    (apply op url rest)))

(defn- wrap-http-op [op & wrappers]
  ((apply comp (conj (vec wrappers) with-original-http-op))
   op))

(def ^:private http-get (wrap-http-op http/http-get with-headers with-auto-reauth))
(def ^:private http-put (wrap-http-op http/http-put with-headers with-auto-reauth))
(def ^:private http-post* (wrap-http-op http/http-post with-headers))
(def ^:private http-post (wrap-http-op http/http-post with-headers with-auto-reauth))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Authentication

(defn- login-endpoint []
  (str (blink-url) "/api/v5/account/login"))

(defn- start-client-registration
  "Login with a new client. This step should be done only once.
  The server may try to verify the login with 2FA. The response
  contains information for the user as to where from he should expect
  the 2FA (phone SMS, email, etc).  The registration is not complete
  until confirmed.  See `verify-pin`."
  [email password unique-id reauth]
  (let [data (merge (default-login-data)
                    {:email email
                     :password password
                     :unique-id unique-id
                     :reauth reauth})]
    (-> (http-post* nil
                    (login-endpoint)
                    {:body data})
        :json)))

(defn- refresh-client-registration [client]
  (start-client-registration (:email client) (:password client) (:unique-id client) true))

(defn- verification-endpoint [client]
  (str (blink-url (:tier client)) "/api/v4/account/" (:account-id client)
       "/client/" (:client-id client) "/pin/verify"))

(defn- verify-pin
  "Send back the 2FA PIN.  This is the second phase of the registration.
  See `start-client-registration`."
  [client pin]
  (-> (http-post* client
                  (verification-endpoint client)
                  {:body {:pin pin}})
      :json))

(defn- register-reply->client [reply email password unique-id]
  (->BlinkClient email password unique-id 
                 (get-in reply [:account :account-id])
                 (get-in reply [:account :client-id])
                 (atom (get-in reply [:auth :token]))
                 (get-in reply [:account :tier])))

(defn register-client
  "Register a new client with the server.  You need to call this
  function only once and save its output.  This function will trigger
  the 2FA from Blink.  You will be asked to enter a PIN that is
  delivered to you via email or SMS. Return a map that identifies the
  client in the successive API calls. An unique id is generated for
  you, but it can also be passed as keyword argument, if
  necessary. See `authenticate-client`."
  [email password & {:keys [unique-id]}]
  (let [unique-id (or unique-id (make-uuid))
        reply (start-client-registration email password unique-id false)]
    (when (get-in reply [:account :client-verification-required])
      (println "Client verification required")
      (doseq [[k v] (:verification reply)]
        (when (:required v)
          (when (= :phone k)
            (println "Phone number" (get-in reply [:phone :number])))
          (println k (dissoc v :required))))
      (println "Type here the PIN you have received:")
      (let [pin (read-line)
            client (register-reply->client reply email password unique-id)
            verification (verify-pin client pin)]
        (when-not (:valid verification)
          (throw (ex-info "failed PIN verification"
                          {:client client :pin pin :reply verification})))
        (println "Write down the following information:")
        (prn (select-keys client [:email :password :unique-id]))
        client))))

(defn authenticate-client
  "Authenticate client.  Call this function to authenticate your client.
  The `unique-id` comes from a previous `register-client`. It is an
  error to call this function with an `unique-id` that is invalid or
  unknown to the server.  Return a map that identifies the client and
  can be passed to all the other API functions."
  [email password unique-id]
  (-> (start-client-registration email password unique-id true)
      (register-reply->client email password unique-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- network-endpoint [client]
  (str (blink-url (:tier client)) "/networks"))

(defn get-networks
  "Get all the configured networks.  Network being a collection of
  cameras hooked to the same Blink hub (aka sync module).  Pass a
  `client` map as returned by `authenticate-client` or
  `register-client`."
  [^BlinkClient client]
  (-> (http-get client (network-endpoint client))
      :json))

(defn- update-network-endpoint [client network]
  (str (blink-url (:tier client)) "/network/" network "/update"))

(defn update-network
  "Update a specific network.  Network being a collection of cameras
  hooked to the same Blink hub (aka sync module).  Pass a `client` and
  a `network` id. Return a map with the network current
  configuration."
  [^BlinkClient client network]
  (-> (http-post client
                 (update-network-endpoint client network))
      :json))

(defn- user-endpoint [client]
  (str (blink-url (:tier client)) "/user"))

(defn get-user
  "Get the user configuration.  Return a map with the user data."
  [^BlinkClient client]
  (-> (http-get client (user-endpoint client))
      :json))

(defn- network-status-endpoint [client network]
  (str (blink-url (:tier client)) "/network/" network))

(defn get-network-status
  "Get the network status.  Return a map with the current configuration
  of `network`."
  [^BlinkClient client network]
  (-> (http-get client (network-status-endpoint client network))
      :json))

(defn- sync-modules-endpoint [client network]
  (str (blink-url (:tier client)) "/network/" network "/syncmodules"))

(defn get-sync-module
  "Get the sync module of `network`. Return a map with the sync module's
  configuration."
  [^BlinkClient client network]
  (-> (http-get client (sync-modules-endpoint client network))
      :json))

(defn- system-state-endpoint [client network action]
  (str (blink-url (:tier client)) "/api/v1/accounts/" (:account-id client) "/networks/" network
       "/state/" action))

(defn system-change-state
  "Change the state of the system.  `action` being either `:arm` or
  `:disarm`.  Return a map with the actions performed on the network."
  [^BlinkClient client network action]
  {:pre (#{:arm :disarm} action)}
  (-> (http-post client (system-state-endpoint client network (name action)))
      :json))

(defn system-arm
  "Shortcut for `(system-change-state client network :arm)`."
  [^BlinkClient client network]
  (system-change-state client network :arm))

(defn system-disarm
  "Shortcut for `(system-change-state client network :disarm)`."
  [^BlinkClient client network]
  (system-change-state client network :disarm))

(defn- command-status-endpoint [client network command-id]
  (str (blink-url (:tier client)) "/network/" network "/command/" command-id))

(defn get-command-status
  "Return the status of command."
  [^BlinkClient client network command-id]
  (-> (http-get client (command-status-endpoint client network command-id))
      :json))

(defn- home-screen-endpoint [client network]
  (str (blink-url (:tier client)) "/api/v3/accounts/"
       (:account-id client) "/homescreen"))

(defn get-home-screen
  "Return the home screen for the specific network.  The home screen
  being a summary of info regarding a network and its devices."
  [^BlinkClient client network]
  (-> (http-get client (home-screen-endpoint client network))
      :json))

(defn- thumbnail-endpoint [client network camera]
  (str (blink-url (:tier client)) "/network/" network "/camera/" camera "/thumbnail"))

(defn new-thumbnail
  "Take a new still photograph with the sepcific camera on the specific
  network. The new thumbnail will replace the current one on the
  mobile app."
  [^BlinkClient client network camera]
  (-> (http-post client (thumbnail-endpoint client network camera))
      :json))

(defn- video-clip-endpoint [client network camera]
  (str (blink-url (:tier client)) "/network/" network "/camera/" camera "/clip"))

(defn new-video-clip
  "Take a new video with the sepcific camera on the specific network."
  [^BlinkClient client network camera]
  (-> (http-post client (thumbnail-endpoint client network camera))
      :json))

(defn- media-endpoint [client]
  (str (blink-url (:tier client)) "/api/v1/accounts/" (:account-id client) "/media/changed"))

(defn- time-string [time]
  (jt/format "yyyy-MM-dd'T'HH:mm:ss" time))

(defn get-videos
  "Get a list of videos currently saved in the cloud. The result is
  paginated and the `:page` number can be passed to specify subsequent
  pages. A starting epoch can be specified with the `:since` keyword
  argument."
  [^BlinkClient client & {:keys [since page]}]
  (-> (http-get client (media-endpoint client)
                {:query-params {:page (or page 0)
                                :since (time-string (or since
                                                        (jt/local-date-time 1970 1 1 0)))}})
      :json))

(defn- camera-info-endpoint [client network camera]
  (str (blink-url (:tier client)) "/network/" network "/camera/" camera "/config"))

(defn get-camera-info
  "Get configuration and status of a specific camera.  Return a map of
  all the data about the camera."
  [^BlinkClient client network camera]
  (-> (http-get client (camera-info-endpoint client network camera))
      :json))

(defn- camera-usage-endpoint [client]
  (str (blink-url (:tier client)) "/api/v1/camera/usage"))

(defn get-camera-usage
  "Get the usage info of all cameras.  Return a map."
  [^BlinkClient client]
  (-> (http-get client (camera-usage-endpoint client))
      :json))

(defn- camera-live-view-endpoint [client network camera]
  (str (blink-url (:tier client)) "/api/v5/accounts/" (:account-id client)
       "/networks/" network "/cameras/" camera "/liveview"))

(defn get-camera-live-view
  "Return a link to the camera live feed."
  [^BlinkClient client network camera]
  (-> (http-post client (camera-live-view-endpoint client network camera))
      :json))

(defn- camera-sensors-endpoint [client network camera]
  (str (blink-url (:tier client)) "/network/" network
       "/camera/" camera "/signals"))

(defn get-camera-sensors
  "Return the status of a camera's sensors."
  [^BlinkClient client network camera]
  (-> (http-get client (camera-sensors-endpoint client network camera))
      :json))

(defn- motion-detection-endpoint [client network camera action]
  (str (blink-url (:tier client)) "/network/" network
       "/camera/" camera "/" (name action)))

(defn change-motion-detection
  "Enable or disable the motion detection of a specific camera. The
  parameter `action` should be either `:eable` or `:disable`"
  [^BlinkClient client network camera action]
  {:pre (#{:enable :disable} action)}
  (-> (http-post client (motion-detection-endpoint client network camera action))
      :json))

(defn motion-detection-enable
  "Short for `(change-motion-detection client network camera :enable)`"
  [^BlinkClient client network camera]
  (change-motion-detection client network camera :enable))

(defn motion-detection-disable
  "Short for `(change-motion-detection client network camera :disable)`"
  [^BlinkClient client network camera]
  (change-motion-detection client network camera :disable))
