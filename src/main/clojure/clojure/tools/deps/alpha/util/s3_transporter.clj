;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.util.s3-transporter
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

(defn parse-url
  [repo-url]
  (let [u (URI/create repo-url)
        host (.getHost u)
        host-parts (str/split host #"\.")
        [path1 & pathr :as path-parts] (remove str/blank? (str/split (.getPath u) #"/"))]
    (println host-parts)
    (case (count host-parts)
      ;; BUCKET
      1 {:bucket host, :repo-path (str/join "/" path-parts)}

      ;; s3.amazonaws.com/BUCKET (REGION = us-east-1)
      3 (when (= host-parts ["s3" "amazonaws" "com"])
          {:region "us-east-1", :bucket path1, :repo-path (str/join "/" pathr)})

      ;; s3.REGION.amazonaws.com/BUCKET
      ;; BUCKET.s3.amazonaws.com (no REGION indicated)
      ;; BUCKET.s3-REGION.amazonaws.com
      4 (when (= (drop 2 host-parts) ["amazonaws" "com"])
          (let [[h1 h2] host-parts]
            (cond (= h1 "s3") {:region h2, :bucket path1, :repo-path (str/join "/" pathr)}
                  (= h2 "s3") {:bucket h1, :repo-path (str/join "/" path-parts)}
                  (str/starts-with? h2 "s3-") {:region (subs h2 3), :bucket h1, :repo-path (str/join "/" path-parts)}
                  :else nil)))

      ;; fail
      nil)))

(defn get-bucket-loc
  [config bucket]
  (try
    (let [s3-client (aws/client config)
          resp (aws/invoke s3-client {:op :GetBucketLocation
                                      :request {:Bucket bucket}})
          region (:LocationConstraint resp)]
      (if (= region "") "us-east-1" region))
    (catch Throwable _ nil)))

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
        url (.getUrl repository)
        {:keys [bucket region repo-path]} (parse-url url)
        _ (when (nil? bucket) (throw (ex-info "Can't parse bucket from url" {:url url})))

        http-client (aws/default-http-client)
        config (cond-> {:api :s3, :http-client http-client}
                 cred-provider (assoc :credentials-provider cred-provider))
        region (if (nil? region) (get-bucket-loc config bucket) region)
        s3-client (aws/client (cond-> config region (assoc :region region)))]
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
