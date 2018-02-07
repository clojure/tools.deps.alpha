;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.extensions.maven
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.util.maven :as maven])
  (:import
    ;; maven-resolver-api
    [org.eclipse.aether RepositorySystem]
    [org.eclipse.aether.resolution ArtifactRequest ArtifactDescriptorRequest VersionRangeRequest]

    ;; maven-resolver-util
    [org.eclipse.aether.util.version GenericVersionScheme]
    ))

(set! *warn-on-reflection* true)

;; Main extension points for using Maven deps

(defmethod ext/canonicalize :mvn
  [lib {:keys [:mvn/version] :as coord} {:keys [mvn/repos mvn/local-repo]}]
  (println "canonicalize" version)
  (if (re-find #"\[|\(" version)
    (let [local-repo (or local-repo maven/default-local-repo)
          system (maven/make-system)
          session (maven/make-session system local-repo)
          artifact (maven/coord->artifact lib coord)
          req (VersionRangeRequest. artifact (mapv maven/remote-repo repos) nil)
          result (.resolveVersionRange system session req)
          newest (.getHighestVersion result)]
      [lib (assoc coord :mvn/version (.toString newest))])
    [lib coord]))

(defmethod ext/dep-id :mvn
  [lib coord config]
  (select-keys coord [:mvn/version :classifier]))

(defmethod ext/manifest-type :mvn
  [lib coord config]
  {:deps/manifest :mvn})

(defmethod ext/coord-summary :mvn [lib {:keys [mvn/version]}]
  (str lib " " version))

(defonce ^:private version-scheme (GenericVersionScheme.))

(defn- parse-version [{version :mvn/version :as coord}]
  (.parseVersion ^GenericVersionScheme version-scheme ^String version))

(defmethod ext/compare-versions [:mvn :mvn]
  [lib coord-x coord-y config]
  (apply compare (map parse-version [coord-x coord-y])))

(defmethod ext/coord-deps :mvn
  [lib coord _manifest {:keys [mvn/repos mvn/local-repo]}]
  (let [local-repo (or local-repo maven/default-local-repo)
        system (maven/make-system)
        session (maven/make-session system local-repo)
        artifact (maven/coord->artifact lib coord)
        req (ArtifactDescriptorRequest. artifact (mapv maven/remote-repo repos) nil)
        result (.readArtifactDescriptor system session req)]
    (into []
      (comp
        (map maven/dep->data)
        (filter #(= (:scope (second %)) "compile"))
        (remove (comp :optional second))
        (map #(update-in % [1] dissoc :scope :optional)))
      (.getDependencies result))))

(defmethod ext/coord-paths :mvn
  [lib coord _manifest {:keys [mvn/repos mvn/local-repo]}]
  (let [local-repo (or local-repo maven/default-local-repo)
        system (maven/make-system)
        session (maven/make-session system local-repo)
        artifact (maven/coord->artifact lib coord)
        req (ArtifactRequest. artifact (mapv maven/remote-repo repos) nil)
        result (.resolveArtifact system session req)
        exceptions (.getExceptions result)]
    (cond
      (.isResolved result) [(.. result getArtifact getFile getAbsolutePath)]
      (.isMissing result) (throw (ex-info (str "Unable to download: [" lib (pr-str (:mvn/version coord)) "]") {:lib lib :coord coord}))
      :else (throw (first (.getExceptions result))))))

(comment
  ;; given a dep, find the child deps
  (ext/coord-deps 'org.clojure/clojure {:mvn/version "1.9.0-alpha17"} :mvn {:mvn/repos maven/standard-repos})

  (ext/coord-deps 'cider/cider-nrepl {:mvn/version "0.17.0-SNAPSHOT"} :mvn {:mvn/repos maven/standard-repos})
  (ext/canonicalize 'joda-time/joda-time {:mvn/version "[2.2,)"} {:mvn/repos maven/standard-repos})

  ;; give a dep, download just that dep (not transitive - that's handled by the core algorithm)
  (ext/coord-paths 'org.clojure/clojure {:mvn/version "1.9.0-alpha17"} :mvn {:mvn/repos maven/standard-repos})

  (parse-version {:mvn/version "1.1.0"})

  (ext/compare-versions {:mvn/version "1.1.0-alpha10"} {:mvn/version "1.1.0-beta1"})

  (ext/coord-deps 'org.clojure/clojure {:mvn/version "1.10.0-master-SNAPSHOT"} :mvn
    {:mvn/repos (merge maven/standard-repos
                  {"sonatype-oss-public" {:url "https://oss.sonatype.org/content/groups/public/"}})})

  (def rr (maven/remote-repo ["sonatype-oss-public" {:url "https://oss.sonatype.org/content/groups/public/"}]))
  )

