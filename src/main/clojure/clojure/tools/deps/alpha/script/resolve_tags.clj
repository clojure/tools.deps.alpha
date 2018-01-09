;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.script.resolve-tags
  (:require
    [clojure.java.io :as jio]
    [clojure.pprint :as pp]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.reader :as reader]
    [clojure.tools.deps.alpha.util.io :refer [printerrln]]
    [clojure.tools.gitlibs :as gitlibs])
  (:import
    [java.io File]))

(set! *warn-on-reflection* true)

(defn- parse-arg
  [parsed arg]
  (cond
    (str/starts-with? arg "--deps-file=")
    (assoc parsed :deps-file (subs arg (count "--deps-file=")))))

(defn- parse-args
  [args]
  (->> args (remove nil?) (reduce parse-arg {})))

(defn- resolve-git-dep
  [counter {:keys [git/url sha tag] :as git-coord}]
  (if (and (not sha) tag)
    (let [sha (gitlibs/resolve url tag)]
      (printerrln "Resolved" tag "=>" sha "in" url)
      (swap! counter inc)
      (assoc git-coord :sha sha))
    git-coord))

(defn- resolve-git-deps
  [counter deps-map]
  (let [f (partial resolve-git-dep counter)]
    (walk/postwalk
      #(cond-> % (and (map? %) (:git/url %)) f)
      deps-map)))

(defn -main
  "Main entry point for resolve-tags script.

  Required:
    --deps-file=deps.edn - deps.edn files in which to resolve git tags

  Read deps.edn, find git coordinates with :tag but without :sha, resolve those
  tags to shas, and over-write the deps.edn."
  [& args]
  (try
    (let [{:keys [deps-file]} (parse-args args)
          deps-map (reader/slurp-deps (jio/file deps-file))
          counter (atom 0)]
      (printerrln "Resolving git tags in" deps-file "...")
      (let [resolved-map (resolve-git-deps counter deps-map)]
        (if (zero? @counter)
          (printerrln "No unresolved tags found.")
          (spit deps-file
            (with-out-str
              (binding [pp/*print-right-margin* 100
                        pp/*print-miser-width* 80
                        clojure.core/*print-namespace-maps* false]
                (pp/pprint resolved-map)))))))
    (catch Throwable t
      (printerrln "Error resolving tags." (.getMessage t))
      (System/exit 1))))