;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.extensions.pom
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.util.maven :as maven])
  (:import
    [java.util List]
    [org.apache.maven.model Model Dependency Exclusion]
    [org.apache.maven.model.building DefaultModelBuildingRequest DefaultModelBuilderFactory]
    [org.apache.maven.model.resolution ModelResolver]
    [org.apache.maven.repository.internal DefaultModelResolver DefaultVersionRangeResolver]
    [org.eclipse.aether RepositorySystem RepositorySystemSession RequestTrace]
    [org.eclipse.aether.impl ArtifactResolver VersionRangeResolver RemoteRepositoryManager]
    [org.eclipse.aether.internal.impl DefaultRemoteRepositoryManager]
    [org.eclipse.aether.spi.locator ServiceLocator]))

(set! *warn-on-reflection* true)

(defn- model-resolver
  ^ModelResolver [{:keys [mvn/repos mvn/local-repo]}]
  (let [local-repo (or local-repo maven/default-local-repo)
        locator ^ServiceLocator @maven/the-locator
        system ^RepositorySystem @maven/the-system
        session (maven/make-session system local-repo)
        artifact-resolver (.getService locator ArtifactResolver)
        version-range-resolver (doto (DefaultVersionRangeResolver.) (.initService locator))
        repo-mgr (doto (DefaultRemoteRepositoryManager.) (.initService locator))
        repos (mapv maven/remote-repo repos)
        ct (.getConstructor org.apache.maven.repository.internal.DefaultModelResolver
             (into-array [RepositorySystemSession RequestTrace String ArtifactResolver VersionRangeResolver RemoteRepositoryManager List]))]
    (.setAccessible ct true)
    (.newInstance ct (object-array [session nil "compile" artifact-resolver version-range-resolver repo-mgr repos]))))

(defn- read-model
  ^Model [dir config]
  (let [pom (jio/file dir "pom.xml")
        req (doto (DefaultModelBuildingRequest.)
              (.setPomFile pom)
              (.setModelResolver (model-resolver config)))
        builder (.newInstance (DefaultModelBuilderFactory.))
        result (.build builder req)]
    (.getEffectiveModel result)))

(defn- model-exclusions->data
  [exclusions]
  (into #{}
    (map (fn [^Exclusion exclusion]
           (symbol (.getGroupId exclusion) (.getArtifactId exclusion))))
    exclusions))

(defn- model-dep->data
  [^Dependency dep]
  (let [scope (.getScope dep)
        optional (.isOptional dep)
        exclusions (model-exclusions->data (.getExclusions dep))
        classifier (.getClassifier dep)]
    [(symbol (.getGroupId dep) (.getArtifactId dep))
     (cond-> {:mvn/version (.getVersion dep)}
       (not (str/blank? classifier)) (assoc :classifier classifier)
       scope (assoc :scope scope)
       optional (assoc :optional true)
       (seq exclusions) (assoc :exclusions exclusions))]))

(defmethod ext/coord-deps :pom
  [_lib {:keys [deps/root] :as coord} _mf config]
  (let [model (read-model root config)]
    (map model-dep->data (.getDependencies model))))

(defmethod ext/coord-paths :pom
  [_lib {:keys [deps/root] :as coord} _mf config]
  nil)