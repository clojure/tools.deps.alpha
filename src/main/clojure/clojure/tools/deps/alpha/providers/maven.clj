;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.providers.maven
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.providers :as providers])
  (:import
    ;; maven-resolver-api
    [org.eclipse.aether RepositorySystem RepositorySystemSession]
    [org.eclipse.aether.artifact Artifact DefaultArtifact]
    [org.eclipse.aether.repository LocalRepository RemoteRepository RemoteRepository$Builder]
    [org.eclipse.aether.resolution ArtifactRequest ArtifactResult ArtifactDescriptorRequest ArtifactDescriptorResult]
    [org.eclipse.aether.graph Dependency Exclusion]
    [org.eclipse.aether.transfer TransferListener TransferEvent TransferResource]

    ;; maven-resolver-spi
    [org.eclipse.aether.spi.connector RepositoryConnectorFactory]
    [org.eclipse.aether.spi.connector.transport TransporterFactory]

    ;; maven-resolver-connector-basic
    [org.eclipse.aether.connector.basic BasicRepositoryConnectorFactory]

    ;; maven-resolver-transport-file
    [org.eclipse.aether.transport.file FileTransporterFactory]

    ;; maven-resolver-transport-http
    [org.eclipse.aether.transport.http HttpTransporterFactory]

    ;; maven-aether-provider
    [org.apache.maven.repository.internal MavenRepositorySystemUtils]

    ;; maven-resolver-util
    [org.eclipse.aether.util.version GenericVersionScheme]
    ))

(set! *warn-on-reflection* true)

;; Remote repositories

(def standard-repos {"central" {:url "https://repo1.maven.org/maven2/"}
                     "clojars" {:url "https://clojars.org/repo/"}})

(defn remote-repo
  ^RemoteRepository [[name {:keys [url]}]]
  (.build (RemoteRepository$Builder. name "default" url)))

;; Local repository

(def ^:private home (System/getProperty "user.home"))
(def default-local-repo (.getAbsolutePath (jio/file home ".m2" "repository")))

(defn make-local-repo
  ^LocalRepository [^String dir]
  (LocalRepository. dir))

;; Maven system and session

;; Delay creation, but then cache Maven RepositorySystem instance
(def ^:private the-system
  (delay
    (let [locator (doto (MavenRepositorySystemUtils/newServiceLocator)
                    (.addService RepositoryConnectorFactory BasicRepositoryConnectorFactory)
                    (.addService TransporterFactory FileTransporterFactory)
                    (.addService TransporterFactory HttpTransporterFactory))]
      (.getService locator RepositorySystem))))

(def ^:private ^TransferListener console-listener
  (reify TransferListener
    (transferStarted [_ event]
      (let [event ^TransferEvent event
            resource (.getResource event)
            name (.getResourceName resource)]
        (println "Downloading:" name "from" (.getRepositoryUrl resource))))
    (transferCorrupted [_ event]
      (println "Download corrupted:" (.. ^TransferEvent event getException getMessage)))
    (transferFailed [_ event]
      #_(println "Download failed:" (.. ^TransferEvent event getException getMessage)))))

(defn- make-session
  ^RepositorySystemSession [^RepositorySystem system local-repo]
  (let [session (MavenRepositorySystemUtils/newSession)
        local-repo-mgr (.newLocalRepositoryManager system session (make-local-repo local-repo))]
    (.setLocalRepositoryManager session local-repo-mgr)
    (.setTransferListener session console-listener)
    session))

(defn- exclusions->data
  [exclusions]
  (into #{}
    (map (fn [^Exclusion exclusion]
           (symbol (.getGroupId exclusion) (.getArtifactId exclusion))))
    exclusions))

(defn- dep->data
  [^Dependency dep]
  (let [scope (.getScope dep)
        optional (.isOptional dep)
        exclusions (exclusions->data (.getExclusions dep))
        ^Artifact artifact (.getArtifact dep)
        classifier (.getClassifier artifact)
        ext (.getExtension artifact)]
    [(symbol (.getGroupId artifact) (.getArtifactId artifact))
     (cond-> {:mvn/version (.getVersion artifact)}
       (not (str/blank? classifier)) (assoc :classifier classifier)
       (not= "jar" ext) (assoc :extension ext)
       scope (assoc :scope scope)
       optional (assoc :optional true)
       (seq exclusions) (assoc :exclusions exclusions))]))

(defn- coord->artifact
  ^Artifact [lib {:keys [mvn/version classifier extension] :or {classifier "", extension "jar"}}]
  (let [version (or version "LATEST")
        artifactId (name lib)
        groupId (or (namespace lib) artifactId)
        artifact (DefaultArtifact. groupId artifactId classifier extension version)]
    artifact))

;; Main extension points for using Maven deps

(defmethod providers/dep-id :mvn
  [lib {:keys [mvn/version classifier] :as coord}]
  {:lib lib
   :version version
   :classifier classifier})

(defmethod providers/manifest-type :mvn
  [lib coord config]
  :mvn)

(defonce ^:private version-scheme (GenericVersionScheme.))

(defn- parse-version [{version :mvn/version :as coord}]
  (.parseVersion ^GenericVersionScheme version-scheme ^String version))

(defmethod providers/compare-versions [:mvn :mvn]
  [coord-x coord-y]
  (apply compare (map parse-version [coord-x coord-y])))

(defmethod providers/coord-deps :mvn
  [lib coord _manifest {:keys [mvn/repos mvn/local-repo]}]
  (let [local-repo (or local-repo default-local-repo)
        system ^RepositorySystem @the-system
        session (make-session system local-repo)
        artifact (coord->artifact lib coord)
        req (ArtifactDescriptorRequest. artifact (mapv remote-repo repos) nil)
        result (.readArtifactDescriptor system session req)]
    (into []
      (comp (map dep->data)
        (filter #(= (:scope (second %)) "compile"))
        (remove (comp :optional second))
        (map #(update-in % [1] dissoc :scope :optional)))
      (.getDependencies result))))

(defmethod providers/coord-paths :mvn
  [lib coord _manifest {:keys [mvn/repos mvn/local-repo]}]
  (let [local-repo (or local-repo default-local-repo)
        system ^RepositorySystem @the-system
        session (make-session system local-repo)
        artifact (coord->artifact lib coord)
        req (ArtifactRequest. artifact (mapv remote-repo repos) nil)
        result (.resolveArtifact system session req)
        exceptions (.getExceptions result)]
    (cond
      (.isResolved result) [(.. result getArtifact getFile getAbsolutePath)]
      (.isMissing result) (throw (Exception. (str "Unable to download: [" lib (pr-str (:mvn/version coord)) "]")))
      :else (throw (first (.getExceptions result))))))

(comment
  ;; given a dep, find the child deps
  (providers/coord-deps 'org.clojure/clojure {:mvn/version "1.9.0-alpha17"} :mvn {:mvn/repos standard-repos})

  ;; give a dep, download just that dep (not transitive - that's handled by the core algorithm)
  (providers/coord-paths 'org.clojure/clojure {:mvn/version "1.9.0-alpha17"} :mvn {:mvn/repos standard-repos})

  (parse-version {:mvn/version "1.1.0"})

  (providers/compare-versions {:mvn/version "1.1.0-alpha10"} {:mvn/version "1.1.0-beta1"})
  )

