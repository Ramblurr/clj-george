(ns clj-george.core-test
  (:require [clojure.test :refer :all]
            [clj-george.core :refer :all]))

(deftest test-remap-filters
  (testing "remap filter params"
    (is (= (remap-filters {
                           :page-size 10
                           :page      0
                           })
           {
            "pageSize" 10
            "page"     0
            }))))
