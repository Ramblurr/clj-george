# clj-george

Interact with Sparkasse's George.

```clj
[clj-george "0.0.0"]
```

## Usage

``` clj
(def config
    {:usernumber "1xxxxxxx"
     :iban       "ATxxxxxxx"
     :account-id "Dxxxxx"
     :token "XXXX"
    })

(defn check-and-get-george-token [config]
  (if (george/token-valid? (:token config))
    config
    (let [chan (a/chan)
          code (george/request-auth (:usernumber config) chan)]
      (println "Verification code" code)
      (let [resp (a/<!! chan)]
        (case (:status resp)
          :error (throw (RuntimeException. (str "George login failed" resp)))
          :done (assoc config :token (:access_token resp)))))))

;; refresh the token
(def config (check-and-get-george-token config))

;; fetch txns
(def txns (let [txns-ch (a/into []
                           (george/get-txns config {:page-size 50 :page 0 :from "2019-07-01"}))]
               (<!! txns-ch)))
               
;; a txn
(first txns)
```


## License

Copyright © 2019 Casey Link

Distributed under the GNU Affero General Public License either version 3.0 or (at
your option) any later version.
