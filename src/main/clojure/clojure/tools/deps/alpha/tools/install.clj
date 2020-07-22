;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.tools.install
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as jio]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.util.maven :as mvn]
    [clojure.tools.deps.alpha.extensions.pom :as pom]
    [clojure.tools.deps.alpha.extensions.local :as local]
    [clojure.string :as str])
  (:import
    [java.io File FileNotFoundException]
    [java.net URL]
    [java.util.jar JarFile]
    [org.apache.maven.model Model]
    [org.apache.maven.model.building UrlModelSource]
    [org.eclipse.aether.artifact DefaultArtifact]
    [org.eclipse.aether.installation InstallRequest]))

(set! *warn-on-reflection* true)

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

;; Install as a tool in deps.edn:
;;   {:aliases
;;    {:install {:fn clojure.tools.deps.alpha.tools.install
;;               :args {:lib my.org.lib
;;                      :version "1.2.3"
;;                      :jar "lib-1.2.3.jar"}}}}

(defn install
  "Install a jar and optional pom to the Maven local cache.
  The group/artifact/version coordinate will be pulled from the
  pom
  if supplied, or the pom in the jar file, or must be provided.
  Any provided attributes override those in the pom/jar.

    Options:
      :jar (required) - path to jar file
      :pom (optional) - path to pom file
      :lib (optional) - qualified symbol like my.org/lib
      :version (optional) - string
      :classifier (optional) - string
      :local-repo (optional) - path to local repo (default = ~/.m2/repository)"
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

(defn print-usage
  []
  (println "Usage: clojure -m clojure.tools.deps.install <params>")
  (println)
  (println "Params:")
  (println "  :jar (required) - path to jar file")
  (println "  :pom (optional) - path to pom file")
  (println "  :lib (optional) - qualified symbol like my.org/lib")
  (println "  :version (optional) - optional, string")
  (println "  :classifier optional) - string")
  (println "  :local-repo (optional) - path to local repo (default = ~/.m2/repository)"))

(defn -main
  [& args]
  (if (or (= [] (seq args)) (= ["-h"] args))
    (do
      (print-usage)
      (System/exit 1))
    (let [kvs (mapv edn/read-string args)
          m (apply hash-map kvs)]
      (install m))))
