(ns clj-george.core
  (:require [clj-http.client :as http]
            [clojure.set :as set]
            [clojure.core.async :as a :refer [<! <!! >! >!! go]]
            )
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def endpoints
  {
   :login        "https://login.sparkasse.at/sts/oauth/authorize"
   :check2fa     "https://login.sparkasse.at/sts/secapp/secondfactor"
   :accounts     "https://api.sparkasse.at/proxy/g/api/my/accounts"
   :transactions "https://api.sparkasse.at/proxy/g/api/my/transactions"
   })

(def params
  {
   :login    {"response_type" "token"
              "client_id"     "georgeclient"}
   :check2fa {"client_id" "georgeclient"}
   })

(defn- auth-header-value [token]
  (str "bearer " token))

(defn- auth-headers [token]
  {"Authorization" (auth-header-value token)})

(defn- init-login [cs]
  (http/get (:login endpoints)
            {:query-params  (:login params)
             :cookie-policy :standard
             :cookie-store  cs}))

(defn- post-login [usernumber cs]
  (http/post (:login endpoints)
             {:redirect-strategy :none
              :cookie-store      cs
              :cookie-policy     :standard
              :query-params      (:login params)
              :form-params
                                 {"j_username"  usernumber
                                  "javaScript"  "jsOK"
                                  "SAMLRequest" "ignore"}
              }))
(defn- check-2fa [cs]
  (http/get (:check2fa endpoints)
            {:as                :json
             :redirect-strategy :none
             :query-params      (:check2fa params)
             :cookie-policy     :standard
             :cookie-store      cs}))


(defn- parse-verification-code [body]
  (second (re-find #"Verification code: <b>(.*)</b>" body)))
(defn- get-token [cs]
  (http/get (:login endpoints)
            {:redirect-strategy :none
             :query-params      (:login params)
             :cookie-policy     :standard
             :cookie-store      cs}))

(defn- parse-token [location-header]
  (second (re-find #"#access_token=(.*?)&" location-header)))

(defn- request-token [chan cs]
  (let [resp            (get-token cs)
        location-header (get-in resp [:headers :location])
        token           (parse-token location-header)]
    (a/put! chan (if token
                   {:status :done :access_token token}
                   {:status :error :message "Failed to request token"}))))

(defn- poll [chan cs]
  (loop [delay    2500
         attempts 0]
    (println delay attempts)
    (if (>= attempts 100)
      (a/put! chan {:status :error :message "Maximum attempts reached"})
      (do
        (Thread/sleep delay)
        (let [resp  (check-2fa cs)
              body  (:body resp)
              delay (:pollingIntervalMs body)]
          (println resp)
          (case (:secondFactorStatus body)
            "PENDING" (recur delay (inc attempts))
            "DONE" (a/put! chan (request-token chan cs))
            "ERROR" (a/put! chan (merge resp {:status :error}))
            (a/put! chan {:status :error :message "Unknown error"})))))))

(defn request-auth [usernumber chan]
  "Given a usernumber and a channel, this function will request an auth token from. In the background it will poll for
  the completion of the second factor authentication. Then it will write the token to the channel. If an error occurs,
  the error is written to the channel instead of the token.

  The return value is a map containing :status of :done and :token, or :status :error and a :message."
  (let [cs       (clj-http.cookies/cookie-store)
        _        (init-login cs)
        response (post-login usernumber cs)
        code     (parse-verification-code (:body response))
        ]
    ;(println response)
    (do
      (a/go (poll chan cs))
      code)))

(defn- fetch
  ([endpoint token]
   (fetch endpoint token {}))
  ([endpoint token params]
   (try+
     (http/get (endpoint endpoints)
               {:headers      (auth-headers token)
                :query-params params
                :as           :json
                :debug        false :debug-body false})
     (catch [:status 401] resp
       (throw+ {:type :token-expired :endpoint endpoint}))
     (catch [:status 400] resp
       (throw+ {:type :bad-request :endpoint endpoint})))))

(defn- find-account-by-iban [accounts iban]
  (->> accounts
       (filter #(= iban (get-in % [:accountno :iban])))
       first))

(defn fetch-accounts [token]
  (let [resp (fetch :accounts token)]
    (:body resp)))

(def filter-params-mapping {
                            :page-size "pageSize"
                            :page      "page"
                            :from      "from"
                            :to        "to"
                            :direction "direction"
                            })

(defn remap-filters [filters]
  (set/rename-keys filters filter-params-mapping))

(defn fetch-transactions
  ([token account-id]
   (println "token" token "account" account-id)
   (fetch-transactions token account-id {:page      0
                                         :page-size 1}))
  ([token account-id filters]
   (let [
         params (merge {"id" account-id} (remap-filters filters))
         _      (println params)
         resp   (fetch :transactions token params
                       )]
     (:body resp))))

(defn next-page [body old-filters]
  (let [{:keys [pageSize currentPage totalPages]} body]
    (when (< currentPage totalPages)
      (merge old-filters
             {:page-size pageSize
              :page      (inc currentPage)
              }))))

(defn ? [v] (println v) v)
(defn paged-request!
  "Page through results of a request using next_page_uri
  Returns a channel with a stream of results"
  [filters {:keys [account-id token]}]
  (let [
        page-size (get filters :page-size 50)
        resp-chan (a/chan (* page-size 2))

        ]
    (a/go-loop [r filters]
      (let [resp  (fetch :transactions token (merge {"id" account-id} (? (remap-filters r))))
            body  (:body resp)
            items (:collection body)]
        (doseq [i items] (>! resp-chan i))
        (if-let [next (next-page body r)]
          (recur next)
          (a/close! resp-chan))
        )
      )
    resp-chan))


(defn get-txns
  "returns a channel containing transactions"
  ([config] (get-txns config {:page-size 50 :page 0}))
  ([config filters]
   (paged-request! filters config)))


(defn account-id [token iban]
  (:transactionAccountId (find-account-by-iban (:collection (fetch-accounts token)) iban)))

(defn token-valid? [token]
  (try+
    (fetch-accounts token)
    true
    (catch Object _ false)))
