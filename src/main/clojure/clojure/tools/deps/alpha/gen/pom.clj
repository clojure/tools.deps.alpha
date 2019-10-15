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
            [clojure.data.xml.tree :as tree]
            [clojure.data.xml.event :as event]
            [clojure.zip :as zip]
            [clojure.tools.deps.alpha.util.maven :as maven]
            [clojure.tools.deps.alpha.util.io :refer [printerrln]])
  (:import [java.io File Reader]
           [clojure.data.xml.node Element]))

(set! *warn-on-reflection* true)

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

(defn- to-dep
  [[lib {:keys [mvn/version exclusions] :as coord}]]
  (let [[group-id artifact-id classifier] (maven/lib->names lib)]
    (if version
      (cond->
        [::pom/dependency
         [::pom/groupId group-id]
         [::pom/artifactId artifact-id]
         [::pom/version version]]

        classifier
        (conj [::pom/classifier classifier])

        (seq exclusions)
        (conj [::pom/exclusions
               (map (fn [excl]
                      [::pom/exclusion
                       [::pom/groupId (or (namespace excl) (name excl))]
                       [::pom/artifactId (name excl)]])
                    exclusions)]))
      (printerrln "Skipping coordinate:" coord))))

(defn- gen-deps
  [deps]
  [::pom/dependencies
   (map to-dep deps)])

(defn- gen-source-dir
  [path]
  [::pom/sourceDirectory path])

(defn- to-repo
  [[name repo]]
  [::pom/repository
   [::pom/id name]
   [::pom/url (:url repo)]])

(defn- gen-repos
  [repos]
  [::pom/repositories
   (map to-repo repos)])

(defn- gen-pom
  [deps [path & paths] repos project-name]
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
       (when (seq paths) (apply printerrln "Skipping paths:" paths))
       [::pom/build (gen-source-dir path)])
     (gen-repos repos)]))

(defn- make-xml-element
  [{:keys [tag attrs] :as node} children]
  (with-meta
    (apply xml/element tag attrs children)
    (meta node)))

(defn- xml-update
  [root tag-path replace-node]
  (let [z (zip/zipper xml/element? :content make-xml-element root)]
    (zip/root
      (loop [[tag & more-tags :as tags] tag-path, parent z, child (zip/down z)]
        (if child
          (if (= tag (:tag (zip/node child)))
            (if (seq more-tags)
              (recur more-tags child (zip/down child))
              (zip/edit child (constantly replace-node)))
            (recur tags parent (zip/right child)))
          (zip/append-child parent replace-node))))))

(defn- replace-deps
  [pom deps]
  (xml-update pom [::pom/dependencies] (xml/sexp-as-element (gen-deps deps))))

(defn- replace-paths
  [pom [path & paths]]
  (when path
    (when (seq paths) (apply printerrln "Skipping paths:" paths))
    (xml-update pom [::pom/build ::pom/sourceDirectory] (xml/sexp-as-element (gen-source-dir path)))))

(defn- replace-repos
  [pom repos]
  (if (seq repos)
    (xml-update pom [::pom/repositories] (xml/sexp-as-element (gen-repos repos)))
    pom))

(defn- parse-xml
  [^Reader rdr]
  (let [roots (tree/seq-tree event/event-element event/event-exit? event/event-node
                (xml/event-seq rdr {:include-node? #{:element :characters :comment}
                                    :skip-whitespace true}))]
    (first (filter #(instance? Element %) (first roots)))))

(defn sync-pom
  [{:keys [deps paths :mvn/repos]} ^File dir]
  (let [repos (remove #(= "https://repo1.maven.org/maven2/" (-> % val :url)) repos)
        pom-file (jio/file dir "pom.xml")
        pom (if (.exists pom-file)
              (with-open [rdr (jio/reader pom-file)]
                (-> rdr
                  parse-xml
                  (replace-deps deps)
                  (replace-paths paths)
                  (replace-repos repos)))
              (gen-pom deps paths repos (.. dir getCanonicalFile getName)))]
    (spit pom-file (xml/indent-str pom))))

(comment
  (require '[clojure.tools.deps.alpha.reader :as r])
  (sync-pom
    (r/read-deps [(jio/file "/usr/local/Cellar/clojure/1.9.0.302/deps.edn")
                  (jio/file "deps.edn")])
    (jio/file "."))
  )
