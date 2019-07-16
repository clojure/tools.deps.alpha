;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.script.effective-deps
  (:require
    [clojure.pprint :as pprint]
    [clojure.tools.cli :as cli]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]]
    [clojure.tools.deps.alpha.script.parse :as parse]
    [clojure.tools.deps.alpha.script.make-classpath :as make-cp])
  (:import
    [clojure.lang ExceptionInfo]))

(def ^:private opts
  [;; deps.edn inputs
   [nil "--config-files PATHS" "Comma delimited list of deps.edn files to merge" :parse-fn parse/parse-files]
   [nil "--config-data EDN" "Final deps.edn data to treat as the last deps.edn file" :parse-fn parse/parse-config]
   ;; aliases
   ["-R" "--resolve-aliases ALIASES" "Concatenated resolve-deps alias names" :parse-fn parse/parse-kws]
   ["-C" "--makecp-aliases ALIASES" "Concatenated make-classpath alias names" :parse-fn parse/parse-kws]
   ["-J" "--jvmopt-aliases ALIASES" "Concatenated jvm option alias names" :parse-fn parse/parse-kws]
   ["-M" "--main-aliases ALIASES" "Concatenated main option alias names" :parse-fn parse/parse-kws]
   ["-A" "--aliases ALIASES" "Concatenated generic alias names" :parse-fn parse/parse-kws]])

(defn parse-opts
  "Parse the command line opts to effective-deps"
  [args]
  (cli/parse-opts args opts))

(defn run
  "Run effective-deps script. See -main for details."
  [{:keys [libs-file cp-file jvm-file main-file skip-cp
           resolve-aliases makecp-aliases jvmopt-aliases main-aliases aliases] :as opts}]
  (let [deps-map (make-cp/combine-deps-files opts)
        resolve-args (deps/combine-aliases deps-map (concat aliases resolve-aliases))
        cp-args (deps/combine-aliases deps-map (concat aliases makecp-aliases))
        jvm-opts (seq (get (deps/combine-aliases deps-map (concat aliases jvmopt-aliases)) :jvm-opts))
        main-opts (seq (get (deps/combine-aliases deps-map (concat aliases main-aliases)) :main-opts))]
    (make-cp/check-aliases deps-map (concat resolve-aliases makecp-aliases jvmopt-aliases main-aliases aliases))
    (printerrln "Merged deps.edn:")
    (binding [*out* *err*]
      (pprint/pprint deps-map))
    (printerrln)

    (printerrln "resolve-deps args:")
    (printerrln (pr-str resolve-args))
    (printerrln)

    (printerrln "make-classpath args:")
    (printerrln (pr-str cp-args))
    (printerrln)

    (printerrln "jvm-opts:")
    (printerrln (pr-str jvm-opts))
    (printerrln)

    (printerrln "main-opts:")
    (printerrln (pr-str main-opts))))

(defn -main
  "Main entry point for effective-deps script.

  Options:
    --config-files=/install/deps.edn,... - comma-delimited list of deps.edn files to merge
    --config-data={...} - deps.edn as data
    -Rresolve-aliases - concatenated resolve-deps alias names
    -Cmakecp-aliases - concatenated make-classpath alias names
    -Jjvmopt-aliases - concatenated jvm-opt alias names
    -Mmain-aliases - concatenated main-opt alias names
    -Aaliases - concatenated generic alias names

  Merges the deps files and prints the effective deps.edn after merge."
  [& args]
  (try
    (let [{:keys [options errors]} (parse-opts args)]
      (when (seq errors)
        (run! println errors)
        (System/exit 1))
      (run options))
    (catch Throwable t
      (printerrln "Error building effective deps." (.getMessage t))
      (when-not (instance? ExceptionInfo t)
        (.printStackTrace t))
      (System/exit 1))))
