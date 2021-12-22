;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.alpha.util.s3-aws-client
  (:require
    [cognitect.aws.client.api :as aws]
    [cognitect.aws.credentials :as creds]))

(defn- aws-creds-provider
  [user pw]
  (reify creds/CredentialsProvider
    (fetch [_]
      {:aws/access-key-id user
       :aws/secret-access-key pw})))

(defn- get-bucket-loc
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

(defn new-s3-client
  [user pw region bucket]
  (let [cred-provider (when (and user pw)
                        (aws-creds-provider user pw))
        config (cond-> {:api :s3}
                 cred-provider (assoc :credentials-provider cred-provider))
        use-region (or region (get-bucket-loc config bucket) "us-east-1")]
    (aws/client (assoc config :region use-region))))