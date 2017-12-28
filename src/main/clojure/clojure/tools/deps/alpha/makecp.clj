;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.makecp
  (:require [clojure.java.io :as jio]
            [clojure.string :as str]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.reader :as reader]
            [clojure.tools.deps.alpha.util.io :refer [printerrln]]

            ;; Load extensions
            [clojure.tools.deps.alpha.extensions.maven]
            [clojure.tools.deps.alpha.extensions.local]
            [clojure.tools.deps.alpha.extensions.git]
            [clojure.tools.deps.alpha.extensions.deps])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(defn- parse-opt
  "Turn opt like `-R:foo:bar` into {:R \":foo:bar\"}."
  [opt]
  {(keyword (subs opt 1 2)) (subs opt 2)})

(defn- parse-arg
  [parsed arg]
  (cond
    ;; comma-delimited list of config paths -> split, turn into files, keep only existing
    (str/starts-with? arg "--config-paths=")
    (let [paths (str/split (subs arg (count "--config-paths=")) #",")]
      (assoc parsed :config-files (->> paths (map jio/file) (filter #(.exists ^File %)))))

    (str/starts-with? arg "--libs-file=")
    (assoc parsed :libs-file (jio/file (subs arg (count "--libs-file="))))

    (str/starts-with? arg "--cp-file=")
    (assoc parsed :cp-file (jio/file (subs arg (count "--cp-file="))))

    :else
    (merge parsed (parse-opt arg))))

(defn- parse-args
  [args]
  (->> args (remove nil?) (reduce parse-arg {})))

(defn- read-kws
  [s]
  (->> (str/split (or s "") #":")
    (remove str/blank?)
    (map
      #(if-let [i (str/index-of % \/)]
         (keyword (subs % 0 i) (subs % (inc i)))
         (keyword %)))))

(defn- lookup-alias
  [deps alias]
  (or
    (get-in deps [:aliases alias])
    (if (str/includes? alias ",")
      (throw (RuntimeException. (str "Invalid alias: " alias ". If specifying multiple aliases, use concatenated keywords, like -R:1.9:bench")))
      (throw (RuntimeException. (str "Alias not defined: " alias))))))

(defn- resolve-deps-aliases
  "Find, read, and combine resolve-deps aliases into a single argsmap
  for resolved-deps."
  [deps resolve-opt]
  (->> resolve-opt
    read-kws
    (map #(lookup-alias deps %))
    (apply merge-with merge)))

(defn- resolve-cp-aliases
  "Find, read, and combine make-classpath aliases into a single argsmap
  for make-classpath."
  [deps cp-opt]
  (let [cp-arg-maps (->> cp-opt read-kws (map #(lookup-alias deps %)))
        combined (apply merge-with merge cp-arg-maps)
        extra-paths (into [] (mapcat :extra-paths) cp-arg-maps)]
    (assoc combined :extra-paths extra-paths)))

(defn- default-git-dir []
  (let [home (System/getProperty "user.home")
        gitlibs (jio/file home ".gitlibs")]
    (.getAbsolutePath gitlibs)))

(defn -main
  "Main entry point for makecp script.

  Required:
    --config-paths=/install/deps.edn,... - comma-delimited list of deps.edn files to merge
    --libs-file=path - libs cache file to write
    --cp-file=path - cp cache file to write
  Options:
    -Rresolve-aliases - concatenated resolve-args alias names
    -Cmake-classpath-aliases - concatenated make-classpath alias names

  Resolves the dependencies and updates the cached libs and/or classpath file.
  The libs file is at <cachedir>/<resolve-aliases>.libs
  The cp file is at <cachedir>/<resolve-aliases>/<cpaliases>.cp"
  [& args]
  (try
    (let [;; Parse args
          {:keys [config-files libs-file cp-file R C]} (parse-args args)

          ;; Read and combine deps files
          deps-map (reader/read-deps config-files)

          ;; Merge default config
          deps-map (merge {:git/config {:cache-dir (default-git-dir))}} deps-map)

          ;; Read or compute+write libs map with resolve-deps
          libs (let [resolve-args (resolve-deps-aliases deps-map R)
                     libs (deps/resolve-deps deps-map resolve-args)]
                 (jio/make-parents libs-file)
                 (spit libs-file (pr-str libs))
                 libs)

          ;; Compute classpath with make-classpath
          cp-args (resolve-cp-aliases deps-map C)
          cp (deps/make-classpath libs (:paths deps-map) cp-args)]

      ;; Write cache file
      (jio/make-parents cp-file)
      (spit cp-file cp))

    ;; Print any exception message to stderr
    (catch Throwable t
      (printerrln "Error building classpath." (.getMessage t))
      (System/exit 1))))

(comment
  (def home (System/getProperty "user.home"))
  (def clojure (str home "/.clojure"))

  (deps/make-classpath
    {'org.clojure/clojure {:mvn/version "1.8.0" :path (str home "/.m2/repository/org/clojure/clojure/1.8.0/clojure-1.8.0.jar")}}
    ["a"]
    (resolve-cp-aliases
      {:aliases {:foo {:extra-paths ["b" "c"]}}}
      ":foo"))
  )
