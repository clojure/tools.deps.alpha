;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.alpha.extensions.maven
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.util.maven :as maven]
    [clojure.tools.deps.alpha.util.session :as session])
  (:import
    [java.io File]

    ;; maven-resolver-api
    [org.eclipse.aether RepositorySystem RepositorySystemSession]
    [org.eclipse.aether.resolution ArtifactRequest ArtifactDescriptorRequest VersionRangeRequest
                                   VersionRequest ArtifactResolutionException]

    ;; maven-resolver-util
    [org.eclipse.aether.util.version GenericVersionScheme]
    ))

(set! *warn-on-reflection* true)

;; Main extension points for using Maven deps

(defmethod ext/canonicalize :mvn
  [lib {:keys [:mvn/version] :as coord} {:keys [mvn/repos mvn/local-repo]}]
  (cond
    (contains? #{"RELEASE" "LATEST"} version)
    (let [local-repo (or local-repo maven/default-local-repo)
          system ^RepositorySystem (session/retrieve :mvn/system #(maven/make-system))
          session ^RepositorySystemSession (session/retrieve :mvn/session #(maven/make-session system local-repo))
          artifact (maven/coord->artifact lib coord)
          req (VersionRequest. artifact (maven/remote-repos repos) nil)
          result (.resolveVersion system session req)]
      (if result
        [lib (assoc coord :mvn/version (.getVersion result))]
        (throw (ex-info (str "Unable to resolve " lib " version: " version) {:lib lib :coord coord}))))

    (maven/version-range? version)
    (let [local-repo (or local-repo maven/default-local-repo)
          system ^RepositorySystem (session/retrieve :mvn/system #(maven/make-system))
          session ^RepositorySystemSession (session/retrieve :mvn/session #(maven/make-session system local-repo))
          artifact (maven/coord->artifact lib coord)
          req (VersionRangeRequest. artifact (maven/remote-repos repos) nil)
          result (.resolveVersionRange system session req)]
      (if (and result (.getHighestVersion result))
        [lib (assoc coord :mvn/version (.toString (.getHighestVersion result)))]
        (throw (ex-info (str "Unable to resolve " lib " version: " version) {:lib lib :coord coord}))))

    :else
    [lib coord]))

(defmethod ext/lib-location :mvn
  [lib {:keys [mvn/version]} {:keys [mvn/local-repo]}]
  (let [[group-id artifact-id classifier] (maven/lib->names lib)]
    {:base (or local-repo maven/default-local-repo)
     :path (.getPath ^File
             (apply jio/file
               (concat (str/split group-id #"\.") [artifact-id version])))
     :classifier classifier
     :type :mvn}))

(defmethod ext/dep-id :mvn
  [_lib coord _config]
  (select-keys coord [:mvn/version]))

(defmethod ext/manifest-type :mvn
  [_lib _coord _config]
  {:deps/manifest :mvn})

(defmethod ext/coord-summary :mvn [lib {:keys [mvn/version]}]
  (str lib " " version))

(defonce ^:private version-scheme (GenericVersionScheme.))

(defn- parse-version [{version :mvn/version :as _coord}]
  (.parseVersion ^GenericVersionScheme version-scheme ^String version))

(defmethod ext/compare-versions [:mvn :mvn]
  [_lib coord-x coord-y _config]
  (apply compare (map parse-version [coord-x coord-y])))

(defmethod ext/coord-deps :mvn
  [lib coord _manifest {:keys [mvn/repos mvn/local-repo]}]
  (let [local-repo (or local-repo maven/default-local-repo)
        system ^RepositorySystem (session/retrieve :mvn/system #(maven/make-system))
        session ^RepositorySystemSession (session/retrieve :mvn/session #(maven/make-session system local-repo))
        artifact (maven/coord->artifact lib coord)
        repos (maven/remote-repos repos)
        req (ArtifactDescriptorRequest. artifact repos nil)
        result (.readArtifactDescriptor system session req)]
    (into []
      (comp
        (map maven/dep->data)
        (filter #(contains? #{"compile" "runtime"} (:scope (second %))))
        (remove (comp :optional second))
        (map #(update-in % [1] dissoc :scope :optional)))
      (.getDependencies result))))

(defn- get-artifact
  [lib coord ^RepositorySystem system ^RepositorySystemSession session mvn-repos]
  (try
    (let [artifact (maven/coord->artifact lib coord)
          req (ArtifactRequest. artifact mvn-repos nil)
          result (.resolveArtifact system session req)]
      (cond
        (.isResolved result) (.. result getArtifact getFile getAbsolutePath)
        (.isMissing result) (throw (ex-info (str "Unable to download: [" lib (pr-str (:mvn/version coord)) "]") {:lib lib :coord coord}))
        :else (throw (first (.getExceptions result)))))
    (catch ArtifactResolutionException e
      (throw (ex-info (.getMessage e) {:lib lib, :coord coord})))))

(defmethod ext/coord-paths :mvn
  [lib coord _manifest {:keys [mvn/repos mvn/local-repo]}]
  (let [local-repo (or local-repo maven/default-local-repo)
        mvn-repos (maven/remote-repos repos)
        system ^RepositorySystem (session/retrieve :mvn/system #(maven/make-system))
        session ^RepositorySystemSession (session/retrieve :mvn/session #(maven/make-session system local-repo))]
    [(get-artifact lib coord system session mvn-repos)]))

(comment
  (ext/lib-location 'org.clojure/clojure {:mvn/version "1.8.0"} {})

  ;; given a dep, find the child deps
  (ext/coord-deps 'org.clojure/clojure {:mvn/version "1.9.0-alpha17"} :mvn {:mvn/repos maven/standard-repos})

  (ext/coord-deps 'cider/cider-nrepl {:mvn/version "0.17.0-SNAPSHOT"} :mvn {:mvn/repos maven/standard-repos})
  (ext/canonicalize 'joda-time/joda-time {:mvn/version "[2.2,)"} {:mvn/repos maven/standard-repos})

  ;; give a dep, download just that dep (not transitive - that's handled by the core algorithm)
  (ext/coord-paths 'org.clojure/clojure {:mvn/version "1.9.0-alpha17"} :mvn {:mvn/repos maven/standard-repos})

  ;; get specific classifier
  (ext/coord-paths 'org.jogamp.gluegen/gluegen-rt$natives-linux-amd64 {:mvn/version "2.3.2"}
    :mvn {:mvn/repos maven/standard-repos})

  (parse-version {:mvn/version "1.1.0"})

  (ext/compare-versions 'org.clojure/clojure {:mvn/version "1.1.0-alpha10"} {:mvn/version "1.1.0-beta1"} {})

  (ext/coord-deps 'org.clojure/clojure {:mvn/version "1.10.0-master-SNAPSHOT"} :mvn
    {:mvn/repos (merge maven/standard-repos
                  {"sonatype-oss-public" {:url "https://oss.sonatype.org/content/groups/public/"}})})

  (def rr (maven/remote-repo ["sonatype-oss-public" {:url "https://oss.sonatype.org/content/groups/public/"}]))
  )

