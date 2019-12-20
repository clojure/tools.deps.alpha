(ns clojure.tools.deps.alpha.util.test-s3-transporter
  (:require
    [clojure.test :refer :all]
    [clojure.tools.deps.alpha.util.s3-transporter :as s3t]))

(deftest test-parse
  (are [u r b p] (= (merge {:region nil, :bucket nil, :repo-path nil} (s3t/parse-url u))
                   {:region r :bucket b :repo-path p})
    "s3://BUCKET/PATH1/PATH2" nil "BUCKET" "PATH1/PATH2"
    "s3://s3.amazonaws.com/BUCKET/PATH1/PATH2" "us-east-1" "BUCKET" "PATH1/PATH2"
    "s3://s3.us-east-1.amazonaws.com/BUCKET/PATH1/PATH2" "us-east-1" "BUCKET" "PATH1/PATH2"
    "s3://BUCKET.s3.amazonaws.com/PATH1/PATH2" nil "BUCKET" "PATH1/PATH2"
    "s3://BUCKET.s3-REGION.amazonaws.com/PATH1/PATH2" "REGION" "BUCKET" "PATH1/PATH2"
    "s3://datomic-releases-1fc2183a.s3-us-east-1.amazonaws.com/maven/releases" "us-east-1" "datomic-releases-1fc2183a" "maven/releases"))

