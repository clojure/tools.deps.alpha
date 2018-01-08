;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.script.generate-manifest
  (:require [clojure.java.io :as jio]
            [clojure.tools.deps.alpha.reader :as reader]
            [clojure.tools.deps.alpha.gen.pom :as pom]
            [clojure.tools.deps.alpha.util.io :refer [printerrln]]
            [clojure.string :as str])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(defn- parse-arg
  [parsed arg]
  (cond
    ;; comma-delimited list of config paths -> split, turn into files, keep only existing
    (str/starts-with? arg "--config-paths=")
    (let [paths (str/split (subs arg (count "--config-paths=")) #",")]
      (assoc parsed :config-files (->> paths (map jio/file) (filter #(.exists ^File %)))))

    (str/starts-with? arg "--gen=")
    (assoc parsed :gen (subs arg (count "--gen=")))))

(defn- parse-args
  [args]
  (->> args (remove nil?) (reduce parse-arg {})))

(defn -main
  "Main entry point for generating a manifest file.

  Required:
    --config-paths=/install/deps.edn,... - comma-delimited list of deps.edn files to merge
    --gen=pom - manifest type to generate"
  [& args]
  (let [{:keys [config-files gen]} (parse-args args)]
    (try
      (pom/sync-pom (reader/read-deps config-files) (jio/file "."))
      (catch Throwable t
        (printerrln "Error generating" gen ":" (.getMessage t))
        (System/exit 1)))))
