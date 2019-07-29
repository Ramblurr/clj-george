(ns clj-george.categories
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]))

(def json-cats-path "resources/categories.json")
(defn read-cats []
  (->  json-cats-path
        slurp
       (json/parse-string true)))

(def cats (:categories (read-cats)))
(def inflow-categories (:income cats))
(def outflow-categories (:outflow cats))
