;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.util.maven
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.util.io :refer [printerrln]])
  (:import
    ;; maven-resolver-api
    [org.eclipse.aether RepositorySystem RepositorySystemSession DefaultRepositoryCache DefaultRepositorySystemSession ConfigurationProperties]
    [org.eclipse.aether.artifact Artifact DefaultArtifact]
    [org.eclipse.aether.repository LocalRepository Proxy RemoteRepository RemoteRepository$Builder]
    [org.eclipse.aether.graph Dependency Exclusion]
    [org.eclipse.aether.transfer TransferListener TransferEvent TransferResource]

    ;; maven-resolver-spi
    [org.eclipse.aether.spi.connector RepositoryConnectorFactory]
    [org.eclipse.aether.spi.connector.transport TransporterFactory]
    [org.eclipse.aether.spi.locator ServiceLocator]

    ;; maven-resolver-connector-basic
    [org.eclipse.aether.connector.basic BasicRepositoryConnectorFactory]

    ;; maven-resolver-transport-file
    [org.eclipse.aether.transport.file FileTransporterFactory]

    ;; maven-resolver-transport-http
    [org.eclipse.aether.transport.http HttpTransporterFactory]

    ;; maven-resolver-transport-wagon
    [org.eclipse.aether.transport.wagon WagonTransporterFactory WagonProvider]

    ;; maven-aether-provider
    [org.apache.maven.repository.internal MavenRepositorySystemUtils]

    ;; maven-resolver-util
    [org.eclipse.aether.util.repository AuthenticationBuilder DefaultProxySelector DefaultMirrorSelector]

    ;; maven-core
    [org.apache.maven.settings DefaultMavenSettingsBuilder Settings Server Mirror]

    ;; maven-settings-builder
    [org.apache.maven.settings.building DefaultSettingsBuilderFactory]

    ;; plexus-utils
    [org.codehaus.plexus.util.xml Xpp3Dom]))

(set! *warn-on-reflection* true)

;; Remote repositories

(def standard-repos {"central" {:url "https://repo1.maven.org/maven2/"}
                     "clojars" {:url "https://repo.clojars.org/"}})

(defn- set-settings-builder
  [^DefaultMavenSettingsBuilder default-builder settings-builder]
  (doto (.. default-builder getClass (getDeclaredField "settingsBuilder"))
    (.setAccessible true)
    (.set default-builder settings-builder)))

(defn- get-settings
  ^Settings []
  (.buildSettings
    (doto (DefaultMavenSettingsBuilder.)
      (set-settings-builder (.newInstance (DefaultSettingsBuilderFactory.))))))

(defn- select-mirror
  ^RemoteRepository [^Settings settings ^RemoteRepository repo]
  (let [mirrors (.getMirrors settings)
        selector (DefaultMirrorSelector.)]
    (run! (fn [^Mirror mirror] (.add selector
                                 (.getName mirror)
                                 (.getUrl mirror)
                                 (.getLayout mirror)
                                 false
                                 (.getMirrorOf mirror)
                                 (.getMirrorOfLayouts mirror)))
      mirrors)
    (.getMirror selector repo)))

(defn- select-proxy
  ^Proxy [^Settings settings ^RemoteRepository repo]
  (->> (.getProxies settings)
    (keep (fn [^org.apache.maven.settings.Proxy proxy-setting]
            (when (.isActive proxy-setting)
              (.. (DefaultProxySelector.)
                (add (Proxy. (.getProtocol proxy-setting)
                             (.getHost proxy-setting)
                             (.getPort proxy-setting)
                             (.. (AuthenticationBuilder.)
                               (addUsername (.getUsername proxy-setting))
                               (addPassword (.getPassword proxy-setting))
                               build))
                     (.getNonProxyHosts proxy-setting))
                (getProxy repo)))))
    first))

(defn remote-repo
  ^RemoteRepository [[^String name {:keys [url]}]]
  (let [^Settings settings (get-settings)
        builder (RemoteRepository$Builder. name "default" url)
        maybe-repo (.build builder)
        mirror (select-mirror settings maybe-repo)
        proxy (select-proxy settings (or mirror maybe-repo))
        ^Server server-setting (->> (.getServers settings) (filter #(= name (.getId ^Server %))) first)]
    (->
      (cond-> builder
        mirror (.setUrl (.getUrl mirror))
        server-setting (.setAuthentication
                         (-> (AuthenticationBuilder.)
                           (.addUsername (.getUsername server-setting))
                           (.addPassword (.getPassword server-setting))
                           (.addPrivateKey (.getPrivateKey server-setting) (.getPassphrase server-setting))
                           (.build)))
        proxy (.setProxy proxy))
      (.build))))

(defn remote-repos
  [repos]
  (->> repos
    (remove (fn [[name config]] (nil? config)))
    (mapv remote-repo)))

;; Local repository

(def ^:private home (System/getProperty "user.home"))
(def default-local-repo (.getAbsolutePath (jio/file home ".m2" "repository")))

(defn make-local-repo
  ^LocalRepository [^String dir]
  (LocalRepository. dir))

;; Maven system and session

;; TODO: in the future this could be user-extensible
(deftype CustomProvider []
  WagonProvider
  (lookup [_ role-hint]
    (if (contains? #{"s3" "s3p"} role-hint)
      (org.springframework.build.aws.maven.PrivateS3Wagon.)
      (throw (ex-info (str "Unknown wagon provider: " role-hint) {:role-hint role-hint}))))
  (release [_ wagon]))

;; Delay creation, but then cache Maven ServiceLocator instance
(def the-locator
  (delay
    (doto (MavenRepositorySystemUtils/newServiceLocator)
      (.addService RepositoryConnectorFactory BasicRepositoryConnectorFactory)
      (.addService TransporterFactory FileTransporterFactory)
      (.addService TransporterFactory HttpTransporterFactory)
      (.addService TransporterFactory WagonTransporterFactory)
      (.setService WagonProvider CustomProvider))))

(defn make-system
  ^RepositorySystem []
  (.getService ^ServiceLocator @the-locator RepositorySystem))

(def ^TransferListener console-listener
  (reify TransferListener
    (transferStarted [_ event]
      (let [event ^TransferEvent event
            resource (.getResource event)
            name (.getResourceName resource)]
        (printerrln "Downloading:" name "from" (.getRepositoryUrl resource))))
    (transferCorrupted [_ event]
      (printerrln "Download corrupted:" (.. ^TransferEvent event getException getMessage)))
    (transferFailed [_ event]
      ;; This happens when Maven can't find an artifact in a particular repo
      ;; (but still may find it in a different repo), ie this is a common event
      #_(printerrln "Download failed:" (.. ^TransferEvent event getException getMessage)))
    (transferInitiated [_ _event])
    (transferProgressed [_ _event])
    (transferSucceeded [_ _event])))

(defn add-server-config [^DefaultRepositorySystemSession session ^Server server]
  (when-let [^Xpp3Dom configuration (.getConfiguration server)]
    (when-let [^Xpp3Dom headers (.getChild configuration "httpHeaders")]
      (.setConfigProperty session
        (str ConfigurationProperties/HTTP_HEADERS "." (.getId server))
        (into {}
          (keep (fn [^Xpp3Dom header]
                  (let [name (.getChild header "name")
                        value (.getChild header "value")]
                    (when (and name value)
                      [(.getValue name) (.getValue value)]))))
          (.getChildren headers "property"))))))

(defn make-session
  ^RepositorySystemSession [^RepositorySystem system local-repo]
  (let [session (MavenRepositorySystemUtils/newSession)
        local-repo-mgr (.newLocalRepositoryManager system session (make-local-repo local-repo))]
    (.setLocalRepositoryManager session local-repo-mgr)
    (.setTransferListener session console-listener)
    (.setCache session (DefaultRepositoryCache.))
    (doseq [^Server server (.getServers (get-settings))]
      (add-server-config session server))
    session))

(defn exclusions->data
  [exclusions]
  (when (and exclusions (pos? (count exclusions)))
    (into #{}
      (map (fn [^Exclusion exclusion]
             (symbol (.getGroupId exclusion) (.getArtifactId exclusion))))
      exclusions)))

(defn dep->data
  [^Dependency dep]
  (let [scope (.getScope dep)
        optional (.isOptional dep)
        exclusions (exclusions->data (.getExclusions dep))
        ^Artifact artifact (.getArtifact dep)
        artifact-id (.getArtifactId artifact)
        classifier (.getClassifier artifact)
        ext (.getExtension artifact)]
    [(symbol (.getGroupId artifact) (if (str/blank? classifier) artifact-id (str artifact-id "$" classifier)))
     (cond-> {:mvn/version (.getVersion artifact)}
       (not= "jar" ext) (assoc :extension ext)
       scope (assoc :scope scope)
       optional (assoc :optional true)
       (seq exclusions) (assoc :exclusions exclusions))]))

(defn lib->names
  "Split lib symbol into [group-id artifact-id classifier]"
  [lib]
  (let [[artifact-id classifier] (str/split (name lib) #"\$")]
    [(or (namespace lib) artifact-id) artifact-id classifier]))

(defn coord->artifact
  ^Artifact [lib {:keys [mvn/version classifier extension] :or {extension "jar"} :as coord}]
  (when classifier
    (throw (ex-info (str "Invalid library spec:\n"
                         (format "  %s %s\n" lib (dissoc coord :deps/manifest))
                         ":classifier in Maven coordinates is no longer supported.\n"
                         "Use groupId/artifactId$classifier in lib names instead.")
                    {:lib lib, :coord coord})))
  (let [[group-id artifact-id classifier] (lib->names lib)
        version (or version "LATEST")
        artifact (DefaultArtifact. group-id artifact-id classifier extension version)]
    artifact))

(defn version-range?
  [version]
  (boolean (re-find #"\[|\(" version)))
