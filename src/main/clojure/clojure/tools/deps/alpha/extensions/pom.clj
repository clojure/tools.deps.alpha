;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.alpha.extensions.pom
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.util.maven :as maven])
  (:import
    [java.io File]
    [java.util List]
    ;; maven-model
    [org.apache.maven.model Model Dependency Exclusion]
    ;; maven-model-builder
    [org.apache.maven.model.building DefaultModelBuildingRequest DefaultModelBuilderFactory ModelSource FileModelSource]
    [org.apache.maven.model.resolution ModelResolver]
    ;; maven-resolver-provider
    [org.apache.maven.repository.internal DefaultVersionRangeResolver]
    ;; maven-resolver-api
    [org.eclipse.aether RepositorySystemSession RequestTrace]
    ;; maven-resolver-impl
    [org.eclipse.aether.impl ArtifactResolver VersionRangeResolver RemoteRepositoryManager]
    [org.eclipse.aether.internal.impl DefaultRemoteRepositoryManager]
    ;; maven-resolver-spi
    [org.eclipse.aether.spi.locator ServiceLocator]
    ;; maven-model
    [org.apache.maven.model Resource]
    ;; maven-core
    [org.apache.maven.project ProjectModelResolver ProjectBuildingRequest$RepositoryMerging]
    ))

(set! *warn-on-reflection* true)

(defn- model-resolver
  ^ModelResolver [{:keys [mvn/repos mvn/local-repo]}]
  (let [local-repo (or local-repo maven/default-local-repo)
        locator ^ServiceLocator @maven/the-locator
        system (maven/make-system)
        session (maven/make-session system local-repo)
        artifact-resolver (.getService locator ArtifactResolver)
        version-range-resolver (doto (DefaultVersionRangeResolver.) (.initService locator))
        repo-mgr (doto (DefaultRemoteRepositoryManager.) (.initService locator))
        repos (mapv maven/remote-repo repos)]
    (ProjectModelResolver. session nil system repo-mgr repos ProjectBuildingRequest$RepositoryMerging/REQUEST_DOMINANT nil)))

(defn read-model
  ^Model [^ModelSource source config]
  (let [props (java.util.Properties.)
        _ (.setProperty props "project.basedir" ".")
        req (doto (DefaultModelBuildingRequest.)
              (.setModelSource source)
              (.setModelResolver (model-resolver config))
              (.setSystemProperties props))
        builder (.newInstance (DefaultModelBuilderFactory.))
        result (.build builder req)]
    (.getEffectiveModel result)))

(defn- read-model-file
  ^Model [^File file config]
  (read-model (FileModelSource. file) config))

(defn- model-exclusions->data
  [exclusions]
  (when (and exclusions (pos? (count exclusions)))
    (into #{}
      (map (fn [^Exclusion exclusion]
             (symbol (.getGroupId exclusion) (.getArtifactId exclusion))))
      exclusions)))

(defn- is-compile
  [^Dependency dep]
  (contains? #{"compile" "runtime"} (.getScope dep)))

(defn- model-dep->data
  [^Dependency dep]
  (let [scope (.getScope dep)
        optional (.isOptional dep)
        exclusions (model-exclusions->data (.getExclusions dep))
        artifact-id (.getArtifactId dep)
        classifier (.getClassifier dep)]
    [(symbol (.getGroupId dep) (if (str/blank? classifier) artifact-id (str artifact-id "$" classifier)))
     (cond-> {:mvn/version (.getVersion dep)}
       scope (assoc :scope scope)
       optional (assoc :optional true)
       (seq exclusions) (assoc :exclusions exclusions))]))

(defn model-deps
  [^Model model]
  (->> (.getDependencies model)
    (filter is-compile)
    (map model-dep->data)))

(defmethod ext/coord-deps :pom
  [_lib {:keys [deps/root] :as _coord} _mf config]
  (let [pom (jio/file root "pom.xml")
        model (read-model-file pom config)]
    (model-deps model)))

(defmethod ext/coord-paths :pom
  [_lib {:keys [deps/root] :as _coord} _mf config]
  (let [pom (jio/file root "pom.xml")
        model (read-model-file pom config)
        srcs (into [(.getCanonicalPath (jio/file root (.. model getBuild getSourceDirectory)))
                    (.getCanonicalPath (jio/file root "src/main/clojure"))]
                   (for [^Resource resource (.. model getBuild getResources)]
                     (.getCanonicalPath (jio/file root (.getDirectory resource)))))]
    (distinct srcs)))

(comment
  (ext/coord-deps 'org.clojure/core.async {:deps/root "../core.async" :deps/manifest :pom}
    :pom {:mvn/repos maven/standard-repos})

  (ext/coord-paths 'org.clojure/core.async {:deps/root "../core.async" :deps/manifest :pom}
    :pom {:mvn/repos maven/standard-repos})
  )
