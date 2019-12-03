;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{skip-wiki true}
   clojure.tools.deps.alpha.script.generate-manifest2
  (:require [clojure.java.io :as jio]
            [clojure.tools.cli :as cli]
            [clojure.tools.deps.alpha.reader :as reader]
            [clojure.tools.deps.alpha.gen.pom :as pom]
            [clojure.tools.deps.alpha.script.parse :as parse]
            [clojure.tools.deps.alpha.script.make-classpath2 :as makecp]
            [clojure.tools.deps.alpha.util.io :refer [printerrln]])
  (:import
    [clojure.lang IExceptionInfo]))

(def ^:private opts
  [[nil "--config-user PATH" "User deps.edn location"]
   [nil "--config-project PATH" "Project deps.edn location"]
   [nil "--config-data EDN" "Final deps.edn data to treat as the last deps.edn file" :parse-fn parse/parse-config]
   [nil "--gen TYPE" "manifest type to generate" :parse-fn keyword]
   ["-R" "--resolve-aliases ALIASES" "Concatenated resolve-deps alias names" :parse-fn parse/parse-kws]
   ["-C" "--makecp-aliases ALIASES" "Concatenated make-classpath alias names" :parse-fn parse/parse-kws]
   ["-A" "--aliases ALIASES" "Concatenated generic alias names" :parse-fn parse/parse-kws]])

(defn -main
  "Main entry point for generating a manifest file.

  Required:
    --config-user PATH - User deps.edn location
    --config-project PATH - Project deps.edn location
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
    (let [{:keys [gen config-user config-project]} options]
      (try
        (let [mod-map (makecp/run-core (merge options
                                         {:install-deps (reader/install-deps)
                                          :user-deps (makecp/read-deps config-user)
                                          :project-deps (makecp/read-deps config-project)}))
              updated-deps (reduce-kv (fn [m lib {:keys [dependents] :as coord}]
                                        (if (seq dependents) m (assoc m lib coord)))
                             {} (:libs mod-map))]
          (pom/sync-pom (merge mod-map {:deps updated-deps}) (jio/file ".")))
        (catch Throwable t
          (printerrln "Error generating" (name gen) "manifest:" (.getMessage t))
          (when-not (instance? IExceptionInfo t)
            (.printStackTrace t))
          (System/exit 1))))))
