(ns fourteatoo.clj-blink.api-test
  (:require [fourteatoo.clj-blink.api :as api]
            [fourteatoo.clj-blink.mock-http :as mock]
            [clojure.test :refer :all]
            [java-time :as jt]))

(def dummy-client
  (api/->BlinkClient "john@smith.address" "password"
                     (atom {:access-token "foo" :refresh-token "bar" :token-type "Buster"})
                     "super" 12345 67890))

(comment
  (mock/call-with-mocks (fn []
                          (api/set-motion-detection dummy-client 123 456 :sleep))))

(use-fixtures :once mock/call-with-mocks)

(defn check-auth-token [response]
  (is (= (str (:token-type @(:auth-tokens dummy-client)) " "
              (:access-token @(:auth-tokens dummy-client)))
         (get-in response [:opts :headers :authorization]))))

(defn assert-empty-body [response]
  (is (nil? (get-in response [:opts :body]))))

(deftest get-user-test
  (let [response (api/get-user dummy-client)]
    (is (= "https://rest-super.immedia-semi.com/user"
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest get-networks-test
  (let [response (api/get-networks dummy-client)]
    (is (= "https://rest-super.immedia-semi.com/networks"
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest update-network-test
  (let [net (rand-int 1000)
        response (api/update-network dummy-client net)]
    (is (= (str "https://rest-super.immedia-semi.com/network/" net "/update")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest get-network-status-test
  (let [net (rand-int 1000)
        response (api/get-network-status dummy-client net)]
    (is (= (str "https://rest-super.immedia-semi.com/network/" net)
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest get-sync-module-test
  (let [net (rand-int 1000)
        response (api/get-sync-module dummy-client net)]
    (is (= (str "https://rest-super.immedia-semi.com/network/" net "/syncmodules")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

;; (remove-ns 'fourteatoo.clj-blink.api-test)

(deftest set-system-state-test
  (let [net (rand-int 1000)
        action (rand-nth [:arm :disarm])
        response (api/set-system-state dummy-client net action)]
    (is (= (str "https://rest-super.immedia-semi.com/api/v1/accounts/"
                (:account-id dummy-client) "/networks/" net "/state/" (name action))
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)
    (is (thrown? Error (api/set-system-state dummy-client net :sleep)))))

;; TODO: system-arm & system-disarm

(deftest get-command-status-test
  (let [net (rand-int 1000)
        command (rand-int 1000)
        response (api/get-command-status dummy-client net command)]
    (is (= (str "https://rest-super.immedia-semi.com/network/" net "/command/" command)
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest get-home-screen-test
  (let [response (api/get-home-screen dummy-client)]
    (is (= (str "https://rest-super.immedia-semi.com/api/v3/accounts/"
                (:account-id dummy-client) "/homescreen")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest new-thumbnail-test
  (let [net (rand-int 1000)
        cam (rand-int 1000)
        response (api/new-thumbnail dummy-client net cam)]
    (is (= (str "https://rest-super.immedia-semi.com/network/"
                net "/camera/" cam "/thumbnail")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest new-video-clip-test
  (let [net (rand-int 1000)
        cam (rand-int 1000)
        response (api/new-video-clip dummy-client net cam)]
    (is (= (str "https://rest-super.immedia-semi.com/network/"
                net "/camera/" cam "/clip")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest get-videos-test
  (let [net (rand-int 1000)
        cam (rand-int 1000)
        page (rand-int 10)
        since (jt/local-date-time)
        response (api/get-videos dummy-client :since since :page page)]
    (is (= (str "https://rest-super.immedia-semi.com/api/v1/accounts/"
                (:account-id dummy-client) "/media/changed")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)
    (is (= {:page page :since (#'api/time-string since)}
           (get-in response [:opts :query-params])))))

(deftest get-camera-info-test
  (let [net (rand-int 1000)
        cam (rand-int 1000)
        response (api/get-camera-info dummy-client net cam)]
    (is (= (str "https://rest-super.immedia-semi.com/network/"
                net "/camera/" cam "/config")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest get-camera-usage-test
  (let [response (api/get-camera-usage dummy-client)]
    (is (= (str "https://rest-super.immedia-semi.com/api/v1/camera/usage")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest get-camera-live-view-test
  (let [net (rand-int 1000)
        cam (rand-int 1000)
        response (api/get-camera-live-view dummy-client net cam)]
    (is (= (str "https://rest-super.immedia-semi.com/api/v5/accounts/"
                (:account-id dummy-client)
                "/networks/" net "/cameras/" cam "/liveview")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest get-camera-sensors-test
  (let [net (rand-int 1000)
        cam (rand-int 1000)
        response (api/get-camera-sensors dummy-client net cam)]
    (is (= (str "https://rest-super.immedia-semi.com/network/"
                net "/camera/" cam "/signals")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest set-motion-detection-test
  (let [net (rand-int 1000)
        cam (rand-int 1000)
        action (rand-nth [:enable :disable])
        response (api/set-motion-detection dummy-client net cam action)]
    (is (= (str "https://rest-super.immedia-semi.com/network/"
                net "/camera/" cam "/" (name action))
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))
