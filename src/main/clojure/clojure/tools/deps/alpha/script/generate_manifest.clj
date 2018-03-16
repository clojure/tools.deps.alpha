;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.script.generate-manifest
  (:require [clojure.java.io :as jio]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.gen.pom :as pom]
            [clojure.tools.deps.alpha.reader :as reader]
            [clojure.tools.deps.alpha.script.parse :as parse]
            [clojure.tools.deps.alpha.script.make-classpath :as makecp]
            [clojure.tools.deps.alpha.util.io :refer [printerrln]])
  (:import
    [clojure.lang ExceptionInfo]))

(def ^:private opts
  [[nil "--config-files PATHS" "Comma delimited list of deps.edn files to merge" :parse-fn parse/parse-files]
   [nil "--config-data EDN" "Final deps.edn data to treat as the last deps.edn file" :parse-fn parse/parse-config]
   [nil "--gen TYPE" "manifest type to generate" :parse-fn keyword]
   ["-R" "--resolve-aliases ALIASES" "Concatenated resolve-deps alias names" :parse-fn parse/parse-kws]
   ["-C" "--makecp-aliases ALIASES" "Concatenated make-classpath alias names" :parse-fn parse/parse-kws]
   ["-A" "--aliases ALIASES" "Concatenated generic alias names" :parse-fn parse/parse-kws]])

(defn -main
  "Main entry point for generating a manifest file.

  Required:
    --config-files DEP_FILES - comma-delimited list of deps.edn files to merge
    --config-data={...} - deps.edn as data
    --gen TYPE - manifest type to generate (currently only pom)

  Options:
    -Raliases - concated resolve-deps alias names, applied to the :deps
    -Aaliases - concatenated generic alias names"
  [& args]
  (let [{:keys [options errors]} (cli/parse-opts args opts)]
    (when (seq errors)
      (run! println errors)
      (System/exit 1))
    (let [{:keys [gen resolve-aliases makecp-aliases aliases]} options]
      (try
        (let [deps-map (makecp/combine-deps-files options)
              resolve-args (deps/combine-aliases deps-map (concat resolve-aliases aliases))
              {:keys [extra-deps override-deps]} resolve-args
              cp-args (deps/combine-aliases deps-map (concat makecp-aliases aliases))
              {:keys [extra-paths]} cp-args
              mod-map (merge-with concat
                        (merge-with merge deps-map {:deps override-deps} {:deps extra-deps})
                        {:paths extra-paths})]
          (pom/sync-pom mod-map (jio/file ".")))
        (catch Throwable t
          (printerrln "Error generating" (name gen) "manifest:" (.getMessage t))
          (when-not (instance? ExceptionInfo t)
            (.printStackTrace t))
          (System/exit 1))))))

(comment
  (-main
    "--config-files" "deps.edn"
    "--gen" "pom")
  )