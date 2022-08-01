;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.alpha.gen.pom
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
  [[lib {:keys [mvn/version exclusions optional] :as coord}]]
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
                    exclusions)])

        optional
        (conj [::pom/optional "true"]))
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
  [{:keys [deps src-paths resource-paths repos group artifact version]
    :or {version "0.1.0"}}]
  (let [[path & paths] src-paths]
    (xml/sexp-as-element
      [::pom/project
       {:xmlns "http://maven.apache.org/POM/4.0.0"
        (keyword "xmlns:xsi") "http://www.w3.org/2001/XMLSchema-instance"
        (keyword "xsi:schemaLocation") "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"}
       [::pom/modelVersion "4.0.0"]
       [::pom/packaging "jar"]
       [::pom/groupId group]
       [::pom/artifactId artifact]
       [::pom/version version]
       [::pom/name artifact]
       (gen-deps deps)
       (when path
         (when (seq paths) (apply printerrln "Skipping paths:" paths))
         [::pom/build (gen-source-dir path)])
       (gen-repos repos)])))

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
            (if-let [next-sibling (zip/right child)]
              (recur tags parent next-sibling)
              (if (seq more-tags)
                (let [new-parent (zip/append-child parent (xml/sexp-as-element tag))
                      new-child (zip/rightmost (zip/down new-parent))]
                  (recur more-tags new-child (zip/down new-child)))
                (zip/append-child parent replace-node))))
          (if (seq more-tags)
            (let [new-parent (zip/append-child parent (xml/sexp-as-element tag))
                  new-child (zip/rightmost (zip/down new-parent))]
              (recur more-tags new-child (zip/down new-child)))
            (zip/append-child parent replace-node)))))))

(defn- replace-deps
  [pom deps]
  (xml-update pom [::pom/dependencies] (xml/sexp-as-element (gen-deps deps))))

(defn- replace-paths
  [pom [path & paths]]
  (if path
    (do
      (when (seq paths) (apply printerrln "Skipping paths:" paths))
      (xml-update pom [::pom/build ::pom/sourceDirectory] (xml/sexp-as-element (gen-source-dir path))))
    pom))

(defn- replace-repos
  [pom repos]
  (if (seq repos)
    (xml-update pom [::pom/repositories] (xml/sexp-as-element (gen-repos repos)))
    pom))

(defn- replace-lib
  [pom lib]
  (if lib
    (-> pom
      (xml-update [::pom/groupId] (xml/sexp-as-element [::pom/groupId (namespace lib)]))
      (xml-update [::pom/artifactId] (xml/sexp-as-element [::pom/artifactId (name lib)]))
      (xml-update [::pom/name] (xml/sexp-as-element [::pom/name (name lib)])))
    pom))

(defn- replace-version
  [pom version]
  (if version
    (xml-update pom [::pom/version] (xml/sexp-as-element [::pom/version version]))
    pom))

(defn- parse-xml
  [^Reader rdr]
  (let [roots (tree/seq-tree event/event-element event/event-exit? event/event-node
                (xml/event-seq rdr {:include-node? #{:element :characters :comment}
                                    :skip-whitespace true}))]
    (first (filter #(instance? Element %) (first roots)))))

(defn- resolve-path-ref
  "Recursively resolve path refs to a coll of paths. Path refs may be:
    string - a path
    keyword - a path alias or the special alias, :paths
    coll of the above"
  [path-ref {:keys [paths aliases] :as edn-map}]
  (let [alias-map (merge aliases {:paths paths})]
    (loop [acc []
           [fpath & rpaths] [path-ref]]
      (cond
        (nil? fpath) acc
        (string? fpath) (recur (conj acc fpath) rpaths)
        (keyword? fpath) (let [res (get alias-map fpath)]
                           (if (coll? res)
                             (recur acc (concat res rpaths))
                             (recur acc (conj res rpaths))))
        (coll? fpath) (recur acc (concat rpaths fpath))))))

(defn- libs->deps
  "Convert libmap to root deps"
  [libs]
  (reduce-kv
    (fn [ret lib {:keys [dependents] :as coord}]
      (if (seq dependents)
        ret
        (assoc ret lib coord)))
    {} libs))

(defn sync-pom
  "Creates or synchronizes a pom given a map of :basis and :params.

  From basis, uses:
    :deps to build <dependencies>
    :paths to build <srcDirectory>
    :mvn/repos to build <repositories> (omits maven central, included by default)

  Params:
    :target-dir Path to target output directory (required)
    :src-pom Path to source pom file (optional, default = \"pom.xml\")
    :lib Symbol of groupId/artifactId (required for new, optional for existing)
    :version String of project version (optional)"
  ([{:keys [basis params]}]
   (let [{:keys [libs paths :mvn/repos]} basis
         root-deps (libs->deps libs)
         {:keys [target-dir src-pom lib version] :or {src-pom "pom.xml"}} params
         resolved-paths (resolve-path-ref paths basis)
         repos (remove #(= "https://repo1.maven.org/maven2/" (-> % val :url)) repos)
         pom-file (jio/file src-pom)
         pom (if (.exists pom-file)
               (with-open [rdr (jio/reader pom-file)]
                 (-> rdr
                   parse-xml
                   (replace-deps root-deps)
                   (replace-paths resolved-paths)
                   (replace-repos repos)
                   (replace-lib lib)
                   (replace-version version)))
               (gen-pom
                 (cond->
                   {:deps root-deps
                    :src-paths resolved-paths
                    :repos repos
                    :group (namespace lib)
                    :artifact (name lib)}
                   version (assoc :version version))))
         target-pom (jio/file target-dir "pom.xml")]
     (spit target-pom (xml/indent-str pom))))

  ;; deprecated arity
  ([{:keys [deps paths :mvn/repos] :as deps-edn} ^File dir]
   (let [artifact-name (.. dir getCanonicalFile getName)
         resolved-paths (resolve-path-ref paths deps-edn)
         src-pom (jio/file dir "pom.xml")]
     (sync-pom {:basis {:deps deps
                        :libs deps
                        :paths resolved-paths
                        :mvn/repos repos}
                :params (merge
                          {:target-dir (.getCanonicalPath dir)}
                          (if (.exists src-pom)
                            {:src-pom (.getCanonicalPath src-pom)}
                            {:lib (symbol artifact-name artifact-name)}))}))))

(comment
  (require '[clojure.tools.deps.alpha :as deps])
  (let [{:keys [install-edn user-edn project-edn]} (deps/find-edn-maps)
        edn (deps/merge-edns [install-edn user-edn project-edn])
        basis (deps/calc-basis edn)]
    (sync-pom basis (jio/file ".")))

  (let [{:keys [install-edn user-edn project-edn]} (deps/find-edn-maps)
        edn (deps/merge-edns [install-edn user-edn project-edn])
        basis (deps/calc-basis edn)]
    (sync-pom
      {:basis basis
       :params {:src-pom "../../tmp/pom2.xml"
                :target-dir "../../tmp"
                :lib 'foo/bar
                :version "1.2.3"}}))
  )
