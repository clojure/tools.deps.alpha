;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.alpha.script.detect-main
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.reader :as reader]
    [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]]
    [clojure.tools.deps.alpha.util.session :as session]
    [clojure.tools.deps.alpha.script.parse :as parse]
    [clojure.tools.deps.alpha.run :as run])
  (:import
    [clojure.lang IExceptionInfo]))

(def ^:private opts
  [;; deps.edn inputs
   [nil "--config-user PATH" "User deps.edn location"]
   [nil "--config-project PATH" "Project deps.edn location"]
   [nil "--config-data EDN" "Final deps.edn data to treat as the last deps.edn file" :parse-fn parse/parse-config]
   ;; aliases
   ["-M" "--main-aliases ALIASES" "Concatenated main option alias names" :parse-fn parse/parse-kws]
   ["-A" "--aliases ALIASES" "Concatenated generic alias names" :parse-fn parse/parse-kws]])

(defn read-deps
  [name]
  (when (not (str/blank? name))
    (let [f (jio/file name)]
      (when (.exists f)
        (reader/slurp-deps f)))))

(defn run
  "Run make-classpath script. See -main for details."
  [{:keys [config-user config-project config-data main-aliases aliases exec-alias] :as opts} args]
  (let [install-deps (reader/install-deps)
        user-deps (read-deps config-user)
        project-deps (read-deps config-project)
        merge-edn (deps/merge-edns [install-deps user-deps project-deps config-data])]
    (if (and (seq args) (str/starts-with? (first args) "-X"))
      (let [{:keys [fname alias]} (run/parse-args args)]
        (str (or fname (get-in merge-edn [:aliases alias :run-fn]) "")))
      (let [main-opts (concat (get (deps/combine-aliases merge-edn (concat main-aliases aliases)) :main-opts) args)]
        (or (second (drop-while #(not= "-m" %) main-opts)) "")))))

(defn -main
  "Main entry point for make-classpath script.

  Options:
    --config-user=path - user deps.edn file (usually ~/.clojure/deps.edn)
    --config-project=path - project deps.edn file (usually ./deps.edn)
    --config-data={...} - deps.edn as data (from -Sdeps)
    -Mmain-aliases - concatenated main-opt alias names
    -Aaliases - concatenated generic alias names

  Parses the aliases to determine the main class being executed, either for
  clojure.main or clojure.tools.deps.alpha.run and print to stdout."
  [& args]
  (try
    (let [{:keys [options arguments errors]} (cli/parse-opts args opts)]
      (when (seq errors)
        (run! printerrln errors)
        (System/exit 1))
      (println (run options arguments)))
    (catch Throwable t
      (printerrln "Warning: can't determine main to compile:" (.getMessage t))
      (when-not (instance? IExceptionInfo t)
        (.printStackTrace t))
      (println "")
      (System/exit 1))))

(comment
  (-main "--config-user=/Users/alex/.clojure/deps.edn" "--config-project=deps.edn" "--" "-m" "foo.bar")
  (-main "--config-user=/Users/alex/.clojure/deps.edn" "--config-project=deps.edn" "--" "-Xclojure.tools.build/build")
  (-main "--config-user=/Users/alex/.clojure/deps.edn" "--config-project=deps.edn" "--" "-X:config")
  (-main "--config-user=/Users/alex/.clojure/deps.edn" "--config-project=deps.edn" "-M:bar" "--" "-m" "foo.bar")
  (-main "--config-user=/Users/alex/.clojure/deps.edn" "--config-project=deps.edn" "-M:foo")
  (-main "--config-user=/Users/alex/.clojure/deps.edn" "--config-project=deps.edn" "-A:foo")
  (-main "--config-user=/Users/alex/.clojure/deps.edn" "--config-project=deps.edn" "--" "x.clj")
  (-main "--config-user=/Users/alex/.clojure/deps.edn" "--config-project=deps.edn" "--" "-e" "nil" "-m" "foo.bar")
  )