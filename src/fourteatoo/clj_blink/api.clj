(ns fourteatoo.clj-blink.api
  "The main API interface.  Call `register-client` or
  `authenticate-client` and, with the returned client map, you can use
  the other primitives."
  (:require
   [fourteatoo.clj-blink.http :as http]
   [fourteatoo.clj-blink.oauth :as auth]
   [java-time :as jt]))

(def ^:private api-domain "immedia-semi.com")

(defn- blink-url [& [tier]]
  (str "https://rest-" (or tier "prod") "." api-domain))

(defrecord BlinkClient [email password auth-tokens tier account-id tulsa-id])

(defn- add-authorization-header [headers type token]
  (assoc headers :authorization (str type " " token)))

(defn- make-headers [client]
  (cond-> (auth/default-headers)
    (:auth-tokens client)
    (add-authorization-header (:token-type @(:auth-tokens client))
                              (:access-token @(:auth-tokens client)))))

(defn- add-headers [client opts]
  (update opts :headers #(merge (make-headers client) %)))

(defn- unauthenticated-exception? [e]
  (= 401 (:http-status (ex-data e))))

(def refresh-client-token)

(defn- update-auth-tokens! [client]
  (let [reply (refresh-client-token client)]
    (reset! (:auth-tokens client) reply))
  client)

(defn- with-headers [op]
  (fn [client url & [opts & rest]]
    (apply op client url (add-headers client opts) rest)))

(defn- with-auto-reauth [op]
  (fn [client url & rest]
    (let [exec #(apply op client url rest)]
      (try
        (exec)
        (catch Exception e
          (if (unauthenticated-exception? e)
            (do
              (update-auth-tokens! client)
              (exec))
            (throw e)))))))

(defn- just-the-json [op]
  (fn [client url & rest]
    (-> (apply op client url rest)
        :json)))

(defn- with-original-http-op [op]
  (fn [client url & rest]
    (apply op url rest)))

(defn- wrap-http-op [op & wrappers]
  ((apply comp (conj (vec wrappers) with-original-http-op))
   op))

;;
;; NOTE: with-headers needs to be last
;;
(def ^:private rest-get (wrap-http-op http/http-get just-the-json with-auto-reauth with-headers))
(def ^:private http-get (wrap-http-op http/http-get with-auto-reauth with-headers))
(def ^:private rest-put (wrap-http-op http/http-put just-the-json with-auto-reauth with-headers))
(def ^:private rest-post (wrap-http-op http/http-post just-the-json with-auto-reauth with-headers))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Authentication

(defn- refresh-client-token [client]
  (auth/refresh-token (:username client) (:refresh-token @(:auth-tokens client))))

(defn- get-tier-info [client]
  (rest-get client (str (blink-url) "/api/v1/users/tier_info")))

(defn- verification-endpoint [client]
  (str (blink-url (:tier client)) "/api/v4/account/" (:account-id client)
       "/client/" (:client-id client) "/pin/verify"))

(defn- make-client [email password tokens & [tier account-id tulsa-id]]
  (->BlinkClient email password (atom tokens) tier account-id tulsa-id))

(defn register-client
  "Register a new client with the server.  You need to call this
  function only once and save its output.  This function may trigger
  the 2FA from Blink.  You will be asked to enter a PIN that is
  delivered to you via email or SMS. Return a map that identifies the
  client in the successive API calls. An unique id is generated for
  you, but it can also be passed as keyword argument, if
  necessary. See `authenticate-client`."
  [username password]
  (->> (auth/register-client username password)
       (make-client username password)))

(defn authenticate-client
  "Authenticate client.  Call this function to authenticate your client.
  The `tokens` comes from a previous `register-client`. Return a
  BlinkClient that identifies the client and can be passed to all the
  other API functions."
  [username password refresh-token]
  (let [tokens (auth/refresh-token username refresh-token)
        client (make-client username password tokens)
        {:keys [tier account-id tulsa-id]} (get-tier-info client)]
    (assoc client
           :tier tier
           :account-id account-id
           :tulsa-id tulsa-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- network-endpoint [client]
  (str (blink-url (:tier client)) "/networks"))

(defn- correct-numeric-keys [m]
  (reduce-kv (fn [m k v]
               (assoc m (parse-long (name k)) v))
             {} m))

(defn get-networks
  "Get all the configured networks.  Network being a collection of
  cameras hooked to the same Blink hub (aka sync module).  Pass a
  `client` map as returned by `authenticate-client` or
  `register-client`."
  [^BlinkClient client]
  (-> (rest-get client (network-endpoint client))
      ;; For some reasons, in the `:summary` map, the network ids are
      ;; received as strings, and thus converted to keywords.  We
      ;; convert them back to their original numeric form for
      ;; uniformity with the ids that are found in the rest of the
      ;; map.
      (update :summary correct-numeric-keys)))

(defn- update-network-endpoint [client network]
  (str (blink-url (:tier client)) "/network/" network "/update"))

(defn update-network
  "Update a specific network.  Network being a collection of cameras
  hooked to the same Blink hub (aka sync module).  Pass a `client` and
  a `network` id. Return a map with the network current
  configuration."
  [^BlinkClient client network]
  (rest-post client
             (update-network-endpoint client network)))

(defn- user-endpoint [client]
  (str (blink-url (:tier client)) "/user"))

(defn get-user
  "Get the user configuration.  Return a map with the user data."
  [^BlinkClient client]
  (rest-get client (user-endpoint client)))

(defn- network-status-endpoint [client network]
  (str (blink-url (:tier client)) "/network/" network))

(defn get-network-status
  "Get the network status.  Return a map with the current configuration
  of `network`."
  [^BlinkClient client network]
  (rest-get client (network-status-endpoint client network)))

(defn- sync-modules-endpoint [client network]
  (str (blink-url (:tier client)) "/network/" network "/syncmodules"))

(defn get-sync-module
  "Get the sync module of `network`. Return a map with the sync module's
  configuration."
  [^BlinkClient client network]
  (rest-get client (sync-modules-endpoint client network)))

(defn- system-state-endpoint [client network action]
  (str (blink-url (:tier client)) "/api/v1/accounts/" (:account-id client) "/networks/" network
       "/state/" action))

(defn set-system-state
  "Change the state of the system.  `action` being either `:arm` or
  `:disarm`.  Return a map with the actions performed on the network."
  [^BlinkClient client network action]
  {:pre [(#{:arm :disarm} action)]}
  (rest-post client (system-state-endpoint client network (name action))))

(defn system-arm
  "Shortcut for `(set-system-state client network :arm)`."
  [^BlinkClient client network]
  (set-system-state client network :arm))

(defn system-disarm
  "Shortcut for `(set-system-state client network :disarm)`."
  [^BlinkClient client network]
  (set-system-state client network :disarm))

(defn- notifications-configuration-endpoint [client]
  (str (blink-url (:tier client)) "/api/v1/accounts/" (:account-id client)
       "/notifications/configuration"))

(defn get-notifications-configuration
  [^BlinkClient client]
  (rest-get client (notifications-configuration-endpoint client)))

;; NOTE: this triggers an internal server error (500)
(defn ^:deprecated set-notifications-configuration [client configuration]
  (rest-post client (notifications-configuration-endpoint client)
             {:notifications configuration}))

(defn- command-status-endpoint [client network command-id]
  (str (blink-url (:tier client)) "/network/" network "/command/" command-id))

(defn get-command-status
  "Return the status of command."
  [^BlinkClient client network command-id]
  (rest-get client (command-status-endpoint client network command-id)))

(defn- home-screen-endpoint [client]
  (str (blink-url (:tier client)) "/api/v3/accounts/"
       (:account-id client) "/homescreen"))

(defn get-home-screen
  "Return the home screen.  The home screen being a summary of info
  regarding a system and its devices."
  [^BlinkClient client]
  (rest-get client (home-screen-endpoint client)))

;; This enpoint has been discontinued (error 426)
(comment
  (defn- sync-events-endpoint [client network]
    (str (blink-url (:tier client)) "/events/network/" network))

  (defn get-sync-events [^BlinkClient client network]
    (rest-get client (sync-events-endpoint client network))))

(defn- thumbnail-endpoint [client network camera]
  (str (blink-url (:tier client)) "/network/" network "/camera/" camera "/thumbnail"))

(defn new-thumbnail
  "Take a new still photograph with the sepcific camera on the specific
  network. The new thumbnail will replace the current one on the
  mobile app."
  [^BlinkClient client network camera]
  (rest-post client (thumbnail-endpoint client network camera)))

(defn- video-clip-endpoint [client network camera]
  (str (blink-url (:tier client)) "/network/" network "/camera/" camera "/clip"))

(defn new-video-clip
  "Take a new video with the sepcific camera on the specific network."
  [^BlinkClient client network camera]
  (rest-post client (video-clip-endpoint client network camera)))

(defn- select-media-endpoint [client]
  (str (blink-url (:tier client)) "/api/v1/accounts/" (:account-id client) "/media/changed"))

(defn- time-string [time]
  (jt/format "yyyy-MM-dd'T'HH:mm:ss" time))

(defn get-videos
  "Get a list of videos currently saved in the cloud. The result is
  paginated and the `:page` number can be passed to specify subsequent
  pages. A starting epoch can be specified with the `:since` keyword
  argument."
  [^BlinkClient client & {:keys [since page]}]
  (rest-get client (select-media-endpoint client)
            {:query-params {:page (or page 0)
                            :since (time-string (or since
                                                    (jt/local-date-time 1970 1 1 0)))}}))

;; This enpoint has been discontinued (error 426)
(comment
  (defn- cameras-endpoint [client network]
    (str (blink-url (:tier client)) "/network/" network "/cameras"))

  (defn get-cameras
    "Get all info about all cameras on the `network`"
    [^BlinkClient client network]
    (rest-get client (cameras-endpoint client network))))

(defn- camera-info-endpoint [client network camera]
  (str (blink-url (:tier client)) "/network/" network "/camera/" camera "/config"))

(defn get-camera-info
  "Get configuration and status of a specific camera.  Return a map of
  all the data about the camera."
  [^BlinkClient client network camera]
  (rest-get client (camera-info-endpoint client network camera)))

(defn- camera-usage-endpoint [client]
  (str (blink-url (:tier client)) "/api/v1/camera/usage"))

(defn get-camera-usage
  "Get the usage info of all cameras.  Return a map."
  [^BlinkClient client]
  (rest-get client (camera-usage-endpoint client)))

(defn- camera-live-view-endpoint [client network camera]
  (str (blink-url (:tier client)) "/api/v5/accounts/" (:account-id client)
       "/networks/" network "/cameras/" camera "/liveview"))

(defn get-camera-live-view
  "Return a link to the camera live feed."
  [^BlinkClient client network camera]
  (rest-post client (camera-live-view-endpoint client network camera)))

(defn- camera-sensors-endpoint [client network camera]
  (str (blink-url (:tier client)) "/network/" network
       "/camera/" camera "/signals"))

(defn get-camera-sensors
  "Return the status of a camera's sensors."
  [^BlinkClient client network camera]
  (rest-get client (camera-sensors-endpoint client network camera)))

(defn- motion-detection-endpoint [client network camera action]
  (str (blink-url (:tier client)) "/network/" network
       "/camera/" camera "/" (name action)))

(defn set-motion-detection
  "Enable or disable the motion detection of a specific camera. The
  parameter `action` should be either `:eable` or `:disable`"
  [^BlinkClient client network camera action]
  {:pre [(#{:enable :disable} action)]}
  (rest-post client (motion-detection-endpoint client network camera action)))

(defn motion-detection-enable
  "Short for `(set-motion-detection client network camera :enable)`"
  [^BlinkClient client network camera]
  (set-motion-detection client network camera :enable))

(defn motion-detection-disable
  "Short for `(set-motion-detection client network camera :disable)`"
  [^BlinkClient client network camera]
  (set-motion-detection client network camera :disable))

(defn- media-endpoint [client path]
  (str (blink-url (:tier client)) path))

(defn fetch-media
  "Fetch the media from the Blink server.  The `path` is found in the
  `:thumbnail` and `:media` entries in the video maps.  See
  `get-videos`."
  [^BlinkClient client path & [as]]
  {:pre [(or (nil? as)
             (#{:stream :reader :byte-array} as))]}
  (-> (http-get client (media-endpoint client path)
                (if as {:as as} {}))
      :body))
