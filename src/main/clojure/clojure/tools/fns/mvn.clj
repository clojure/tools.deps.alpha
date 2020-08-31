;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.fns.mvn
  (:require [clojure.java.io :as jio]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.extensions.pom :as pom]
            [clojure.tools.deps.alpha.extensions.local :as local]
            [clojure.tools.deps.alpha.gen.pom :as gen-pom]
            [clojure.tools.deps.alpha.util.maven :as mvn]
            [clojure.tools.deps.alpha.util.io :refer [printerrln]])
  (:import
    [java.io File FileNotFoundException]
    [java.net URL]
    [java.util.jar JarFile]
    [org.apache.maven.model Model]
    [org.apache.maven.model.building UrlModelSource]
    [org.eclipse.aether.artifact DefaultArtifact]
    [org.eclipse.aether.installation InstallRequest]
    [clojure.lang IExceptionInfo]))

(set! *warn-on-reflection* true)

(defn- read-basis
  "Read runtime and return the runtime basis"
  []
  (-> (System/getProperty "clojure.basis") jio/file slurp edn/read-string))

;;;; Generate pom

(defn pom
  "Sync local pom.xml"
  [_]
  (try
    (let [basis (read-basis)
          ;; treat all transitive deps as top-level deps
          updated-deps (reduce-kv (fn [m lib {:keys [dependents] :as coord}]
                                    (if (seq dependents) m (assoc m lib coord)))
                         {} (:libs basis))]
      (gen-pom/sync-pom (merge basis {:deps updated-deps}) (jio/file ".")))
    (catch Throwable t
      (printerrln "Error generating pom manifest:" (.getMessage t))
      (when-not (instance? IExceptionInfo t)
        (.printStackTrace t))
      (System/exit 1))))

;;;; Install jar into local repository

(defn- pom-attributes
  [^Model model]
  {:group-id (.getGroupId model)
   :artifact-id (.getArtifactId model)
   :version (.getVersion model)})

(defn- read-pom-file
  [pom]
  (let [pom-file (jio/file pom)]
    (when (.exists pom-file)
      (pom-attributes (pom/read-model-file pom-file (deps/root-deps))))))

(defn- read-pom-in-jar
  [jar]
  (let [jar-file (jio/file jar)]
    (when (or (nil? jar) (not (.exists jar-file)))
      (throw (FileNotFoundException. (str "Jar file not found: " jar))))
    (when-let [path (local/find-pom (JarFile. jar-file))]
      (let [url (URL. (str "jar:file:" jar "!/" path))
            src (UrlModelSource. url)
            model (pom/read-model src (deps/root-deps))]
        (pom-attributes model)))))

(defn- output-path
  [local-repo group-id artifact-id version]
  (let [path-parts (concat
                     [(or local-repo mvn/default-local-repo)]
                     (str/split group-id #"\.")
                     [artifact-id version])]
    (.getAbsolutePath ^File (apply jio/file path-parts))))

(defn install
  "Install a jar and optional pom to the Maven local cache.
  The group/artifact/version coordinate will be pulled from the
  pom if supplied, or the pom in the jar file, or must be provided.
  Any provided attributes override those in the pom or jar.

  Options:
    :jar (required) - path to jar file
    :pom (optional) - path to pom file
    :lib (optional) - qualified symbol like my.org/lib
    :version (optional) - string
    :classifier (optional) - string
    :local-repo (optional) - path to local repo (default = ~/.m2/repository)

  Execute ad-hoc:
    clj -X:deps mvn/install :jar '\"foo.jar\"'"
  [{:keys [lib jar pom classifier local-repo] :as opts}]
  (println "Installing" jar (if pom (str "and " pom) ""))
  (let [{:keys [group-id artifact-id version]} (merge (if pom
                                                        (read-pom-file pom)
                                                        (read-pom-in-jar jar))
                                                 (when lib
                                                   {:group-id (when lib (namespace lib))
                                                    :artifact-id (when lib (name lib))})
                                                 (when-let [v (:version opts)]
                                                   {:version v}))
        jar-file (jio/file jar)
        pom-file (jio/file pom)
        system (mvn/make-system)
        session (mvn/make-session system (or local-repo mvn/default-local-repo))
        jar-artifact (.setFile (DefaultArtifact. group-id artifact-id classifier "jar" version) jar-file)
        artifacts (cond-> [jar-artifact]
                    (and pom (.exists pom-file)) (conj (.setFile (DefaultArtifact. group-id artifact-id classifier "pom" version) pom-file)))
        install-request (.setArtifacts (InstallRequest.) artifacts)]
    (.install system session install-request)
    (println "Installed to" (output-path local-repo group-id artifact-id version))))