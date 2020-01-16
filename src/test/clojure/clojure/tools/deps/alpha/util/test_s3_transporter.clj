(ns clojure.tools.deps.alpha.util.test-s3-transporter
  (:require
    [clojure.test :refer :all]
    [clojure.tools.deps.alpha.util.s3-transporter :as s3t]
    [clojure.tools.deps.alpha.util.maven :as mvn])
  (:import
    [java.io File]
    [java.net URI]
    [org.eclipse.aether.spi.connector.transport Transporter GetTask TransportListener]))

(set! *warn-on-reflection* true)

(deftest test-parse
  (are [u r b p] (= (merge {:region nil, :bucket nil, :repo-path nil}
                      (s3t/parse-url (mvn/remote-repo [nil {:url u}])))
                   {:region r :bucket b :repo-path p})
    "s3://BUCKET/PATH1/PATH2" nil "BUCKET" "PATH1/PATH2"
    "s3://BUCKET/PATH1/PATH2?region=REGION" "REGION" "BUCKET" "PATH1/PATH2"))

(defn downloader
  [repo url path]
  (let [system (mvn/make-system)
        session (mvn/make-session system "/Users/alex/.m2/repository")
        remote-repo (mvn/remote-repo [repo {:url url}])
        transporter (s3t/new-transporter session remote-repo)
        task (GetTask. (URI/create path))
        temp (File/createTempFile "dload-" nil)]
    (.setDataFile task temp)
    (.setListener task (proxy [TransportListener] []
                         (transportStarted [_ _])
                         (transportProgressed [_])))
    (.get ^Transporter transporter task)
    (slurp temp)))

(comment
  (downloader "datomic" "s3://datomic-releases-1fc2183a/maven/releases" "com/datomic/ion/0.9.35/ion-0.9.35.pom")
  (downloader "datomic" "s3://datomic-releases-1fc2183a/maven/releases?region=us-east-1" "com/datomic/ion/0.9.35/ion-0.9.35.pom")
  )