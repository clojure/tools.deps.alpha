;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.cli
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.reader :as reader]
            [clojure.tools.deps.alpha.script.parse :as parse]))

(def opts
  [;; deps.edn inputs
   [nil "--config-files PATHS" "Comma delimited list of deps.edn files to merge" :parse-fn parse/parse-files]
   [nil "--config-data EDN" "Final deps.edn data to treat as the last deps.edn file" :parse-fn parse/parse-config]
   ;; output files
   [nil "--libs-file PATH" "Libs cache file to write"]
   [nil "--cp-file PATH" "Classpatch cache file to write"]
   [nil "--jvm-file PATH" "JVM options file"]
   [nil "--main-file PATH" "Main options file"]
   [nil "--skip-cp" "Skip writing .cp and .libs files"]
   ;; aliases
   ["-R" "--resolve-aliases ALIASES" "Concatenated resolve-deps alias names" :parse-fn parse/parse-kws]
   ["-C" "--makecp-aliases ALIASES" "Concatenated make-classpath alias names" :parse-fn parse/parse-kws]
   ["-J" "--jvmopt-aliases ALIASES" "Concatenated jvm option alias names" :parse-fn parse/parse-kws]
   ["-M" "--main-aliases ALIASES" "Concatenated main option alias names" :parse-fn parse/parse-kws]
   ["-A" "--aliases ALIASES" "Concatenated generic alias names" :parse-fn parse/parse-kws]])

;; workaround until scripts return programmatic edn
(defn- clojure-env-as-text
  []
  (let [{:keys [out exit] :as result} (sh/sh "clojure" "-Sverbose" "-e" ":ran")]
    (if (zero? exit)
      out
      (throw (ex-info "Unable to locate Clojure's edn files" result)))))

(defn- parse-clojure-env
  [env-text]
  (let [[install-edn config-edn local-edn] (-> (re-find #"config_paths = ([^\n]+)" env-text)
                                               second
                                               (str/split #" "))]
    {:install-edn install-edn
     :config-edn config-edn
     :local-edn local-edn}))

(def get-clojure-env
  "Returns a map describing the environment known to clj/clojure."
  (memoize (comp parse-clojure-env clojure-env-as-text)))

(defn parse
  [args]
  (cli/parse-opts args opts))

  ;; order-sensitive, config-files are are list
(defn add-env
  [parsed-opts clojure-env]
  (let [{:keys [local-edn config-edn install-edn]} clojure-env]
    (merge-with into
                {:config-files [install-edn config-edn local-edn]}
                parsed-opts)))

(defn prepare-run
  "Given parsed-opts, return all the data structures describing
a program to be run. This is useful for e.g. tools that want to match
the behavior for the Clojure runner."
  [parsed-opts]
  (let [{:keys [config-files config-data             
                libs-file cp-file jvm-file main-file 
                skip-cp                              
                resolve-aliases makecp-aliases jvmopt-aliases main-aliases aliases]} parsed-opts
                deps-map (reader/read-deps config-files)
                deps-map (if config-data (reader/merge-deps [deps-map config-data]) deps-map)
                resolve-args (deps/combine-aliases deps-map (concat aliases resolve-aliases))
                cp-args (deps/combine-aliases deps-map (concat aliases makecp-aliases))
                libs (deps/resolve-deps deps-map resolve-args)
                cp (deps/make-classpath libs (:paths deps-map) cp-args)]
    {:deps-map deps-map
     :lib-map libs
     :classpath cp}))



