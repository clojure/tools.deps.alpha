;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.gen.pom
  (:require [clojure.java.io :as jio]
            [clojure.data.xml :as xml]
            [clojure.zip :as zip])
  (:import [java.io File]))

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

(defn- to-dep
  [[lib coord]]
  (if (:mvn/version coord)
    [::pom/dependency
     [::pom/groupId (or (namespace lib) (name lib))]
     [::pom/artifactId (name lib)]
     [::pom/version (:mvn/version coord)]]
    (println "Skipping coordinate:" coord)))

(defn- gen-deps
  [deps]
  [::pom/dependencies
   (map to-dep deps)])

(defn- gen-source-dir
  [path]
  [::pom/sourceDirectory path])

(defn- gen-pom
  [deps [path & paths] project-name]
  (xml/sexp-as-element
    [::pom/project
     {:xmlns "http://maven.apache.org/POM/4.0.0"
               (keyword "xmlns:xsi") "http://www.w3.org/2001/XMLSchema-instance"
               (keyword "xsi:schemaLocation") "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"}
     [::pom/modelVersion "4.0.0"]
     [::pom/groupId project-name]
     [::pom/artifactId project-name]
     [::pom/version "0.1.0"]
     [::pom/name project-name]
     (gen-deps deps)
     (when path
       (when (seq paths) (apply println "Skipping paths:" paths))
       [::pom/build (gen-source-dir path)])]))

(defn- make-xml-element
  [{:keys [tag attrs] :as node} children]
  (with-meta
    (apply xml/element tag attrs children)
    (meta node)))

(defn- xml-replace
  [root tag replace-node]
  (let [z (zip/zipper xml/element? :content make-xml-element root)]
    (loop [loc z]
      (if (zip/end? loc)
        (zip/root loc)
        (if (= tag (:tag (zip/node loc)))
          (recur (zip/next (zip/edit loc (constantly replace-node))))
          (recur (zip/next loc)))))))

(defn- replace-deps
  [pom deps]
  (xml-replace pom ::pom/dependencies (xml/sexp-as-element (gen-deps deps))))

(defn- replace-paths
  [pom [path & paths]]
  (when path
    (when (seq paths) (apply println "Skipping paths:" paths))
    (xml-replace pom ::pom/sourceDirectory (xml/sexp-as-element (gen-source-dir path)))))

(defn sync-pom
  [{:keys [deps paths] :as c} ^File dir]
  (let [pom-file (jio/file dir "pom.xml")
        pom (if (.exists pom-file)
              (-> (jio/reader pom-file)
                xml/parse
                (replace-deps deps)
                (replace-paths paths))
              (gen-pom deps paths (.. dir getCanonicalFile getName)))]
    (spit pom-file (xml/indent-str pom))))

(comment
  (require '[clojure.tools.deps.alpha.reader :as r])
  (sync-pom
    (r/read-deps [(jio/file "/usr/local/Cellar/clojure/1.9.0-beta1.229/deps.edn")
                  (jio/file "deps.edn")])
    (jio/file "."))

  )
