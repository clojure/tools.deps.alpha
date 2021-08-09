;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.alpha.util.s3-transporter
  (:refer-clojure :exclude [peek get])
  (:require
    [cognitect.aws.client.api :as aws]
    [cognitect.aws.credentials :as creds]
    [clojure.string :as str])
  (:import
    [java.io InputStream OutputStream IOException]
    [java.net URI]
    [java.nio ByteBuffer]
    [org.eclipse.aether RepositorySystemSession]
    [org.eclipse.aether.repository RemoteRepository AuthenticationContext]
    [org.eclipse.aether.spi.connector.transport Transporter PeekTask GetTask]))

(set! *warn-on-reflection* true)

(defn s3-peek
  "Returns nil if path exists, anomaly category otherwise"
  [s3-client bucket path]
  (let [s3-response (aws/invoke s3-client
                      {:op :HeadObject,
                       :request {:Bucket bucket, :Key path}})]
    (:cognitect.anomalies/category s3-response)))

(defn stream-copy
  [^OutputStream os ^InputStream is ^long offset on-read]
  (let [bb (ByteBuffer/allocate 32768)
        ba (.array bb)]
    (when (pos? offset)
      (let [skipped (.skip is offset)]
        (when-not (= skipped offset)
          (throw (IOException. (str "Failed skipping " offset ", only skipped " skipped))))))
    (try
      (loop []
        (let [read (.read is ba)]
          (when (<= 0 read)
            (.write os ba 0 read)
            (.rewind bb)
            (.limit bb read)
            (on-read bb)
            (recur))))
      (finally
        (.close is)
        (.close os)))))

(defn s3-get-object
  [s3-client bucket path ^OutputStream output-stream offset on-read]
  (let [s3-response (aws/invoke s3-client {:op :GetObject, :request {:Bucket bucket, :Key path}})
        is ^InputStream (:Body s3-response)]
    (if is
      (stream-copy output-stream is offset on-read)
      (throw (ex-info "Artifact not found" {:bucket bucket, :path path, :reason :cognitect.anomalies/not-found})))))

;; s3://BUCKET/PATH?region=us-east-1
(defn parse-url
  [^RemoteRepository repo]
  (let [u (URI/create (.getUrl repo))
        host (.getHost u)
        path (str/join "/" (remove str/blank? (str/split (.getPath u) #"/")))
        query (.getQuery u)
        kvs (when query (str/split query #"&"))
        {:strs [region]} (reduce (fn [m kv] (let [[k v] (str/split kv #"=")] (assoc m k v))) {} kvs)]
    {:bucket host, :region region, :repo-path path}))

(defn get-bucket-loc
  [config bucket]
  (let [s3-client (aws/client (merge {:region "us-east-1"} config))
        resp (try
               (aws/invoke s3-client {:op :GetBucketLocation
                                      :request {:Bucket bucket}})
               (catch Throwable _ nil))
        region (:LocationConstraint resp)]
    (cond
      (nil? region) nil
      (= region "") "us-east-1"
      :else region)))

(defn new-transporter
  [^RepositorySystemSession session ^RemoteRepository repository]
  (let [auth-context (AuthenticationContext/forRepository session repository)
        user (when auth-context (.get auth-context AuthenticationContext/USERNAME))
        pw (when auth-context (.get auth-context AuthenticationContext/PASSWORD))

        cred-provider (when (and user pw)
                        (reify creds/CredentialsProvider
                          (fetch [_]
                            {:aws/access-key-id user
                             :aws/secret-access-key pw})))
        on-close #(when auth-context (.close auth-context))
        {:keys [bucket region repo-path]} (parse-url repository)


        config (cond-> {:api :s3}
                 cred-provider (assoc :credentials-provider cred-provider))
        use-region (or region (get-bucket-loc config bucket) "us-east-1")
        s3-client (aws/client (assoc config :region use-region))]
    (reify Transporter
      (^void peek [_ ^PeekTask peek-task]
        (let [path (.. peek-task getLocation toString)
              full-path (str repo-path "/" path)
              res (s3-peek s3-client bucket full-path)]
          (when res
            (throw (ex-info "Artifact not found" {:bucket bucket, :path path, :reason res})))))
      (^void get [_ ^GetTask get-task]
        (let [path (.. get-task getLocation toString)
              full-path (str repo-path "/" path)
              offset (.getResumeOffset get-task)
              os (.newOutputStream get-task (> offset 0))
              listener (.getListener get-task)]
          (.transportStarted listener offset -1)
          (s3-get-object s3-client bucket full-path os offset #(.transportProgressed listener %))))
      (classify [_ throwable]
        (if (= (-> throwable ex-data :reason) :cognitect.anomalies/not-found)
          Transporter/ERROR_NOT_FOUND
          Transporter/ERROR_OTHER))
      ;;(put [_ ^PutTask put-task])   ;; not supported
      (close [_]
        (when on-close (on-close))))))

(comment
  (def s3-client (aws/client {:api :s3
                              :region :us-east-1})) ;; use ambient creds

  (def resp (aws/invoke s3-client {:op :GetObject
                                   :request {:Bucket "datomic-releases-1fc2183a"
                                             :Key "maven/releases/com/datomic/ion/0.9.35/ion-0.9.35.pom"}}))

  (aws/invoke s3-client {:op :GetBucketLocation
                         :request {:Bucket "datomic-releases-1fc2183a"}})

  (aws/invoke s3-client {:op :GetObject
                         :request {:Bucket "datomic-releases-1fc2183a"
                                   :Key "/maven/releases/com/datomic/ion/0.9.35/ion-0.9.35.pom"}})

  (s3-peek s3-client "datomic-releases-1fc2183a" "/maven/releases/com/datomic/ion/0.9.35/ion-0.9.35.pom")

  (with-open [os (java.io.FileOutputStream. "download.pom")]
    (s3-get-object s3-client "datomic-releases-1fc2183a" "/maven/releases/com/datomic/ion/0.9.35/ion-0.9.35.pom"
      os 0 #(println "read data" %)))

  (def ann (s3-peek s3-client "datomic-releases-1fc2183a" "/maven/releases/com/datomic/ion/0.9.35/ion-0.9.35.foo"))

  (aws/ops s3-client)
  (aws/invoke s3-client {:op :GetBucketAcl, :request {:Bucket "datomic-releases-1fc2183a"}})
  (aws/validate-requests s3-client true)
  (aws/request-spec-key s3-client :GetObject)
  (clojure.repl/doc :cognitect.aws.s3/GetObjectRequest)
  (aws/doc s3-client :cognitect.aws.s3/GetBucketLocation)
  (clojure.repl/doc aws/request-spec-key)
  )
