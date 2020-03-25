;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.alpha.script.make-classpath2
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.reader :as reader]
    [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]]
    [clojure.tools.deps.alpha.util.session :as session]
    [clojure.tools.deps.alpha.script.parse :as parse])
  (:import
    [clojure.lang IExceptionInfo]))

(def ^:private opts
  [;; deps.edn inputs
   [nil "--config-user PATH" "User deps.edn location"]
   [nil "--config-project PATH" "Project deps.edn location"]
   [nil "--config-data EDN" "Final deps.edn data to treat as the last deps.edn file" :parse-fn parse/parse-config]
   ;; output files
   [nil "--libs-file PATH" "Libs cache file to write"]
   [nil "--cp-file PATH" "Classpatch cache file to write"]
   [nil "--jvm-file PATH" "JVM options file"]
   [nil "--main-file PATH" "Main options file"]
   [nil "--basis-file PATH" "Basis file"]
   [nil "--skip-cp" "Skip writing .cp and .libs files"]
   ;; aliases
   ["-R" "--resolve-aliases ALIASES" "Concatenated resolve-deps alias names" :parse-fn parse/parse-kws]
   ["-C" "--makecp-aliases ALIASES" "Concatenated make-classpath alias names" :parse-fn parse/parse-kws]
   ["-J" "--jvmopt-aliases ALIASES" "Concatenated jvm option alias names" :parse-fn parse/parse-kws]
   ["-M" "--main-aliases ALIASES" "Concatenated main option alias names" :parse-fn parse/parse-kws]
   ["-T" "--tool-aliases ALIASES" "Concatenated tool alias names" :parse-fn parse/parse-kws]
   ["-A" "--aliases ALIASES" "Concatenated generic alias names" :parse-fn parse/parse-kws]
   ;; options
   [nil "--trace" "Emit trace log to trace.edn"]
   [nil "--threads THREADS" "Threads for concurrent downloads"]])

(defn parse-opts
  "Parse the command line opts to make-classpath"
  [args]
  (cli/parse-opts args opts))

(defn check-aliases
  "Check that all aliases are known and warn if aliases are undeclared"
  [deps aliases]
  (when-let [unknown (seq (remove #(contains? (:aliases deps) %) (distinct aliases)))]
    (printerrln "WARNING: Specified aliases are undeclared:" (vec unknown))))

(defn run-core
  "Run make-classpath script from/to data (no file stuff). Returns:
    {;; Main outputs:
     :libs lib-map          ;; from resolve-deps, .libs file
     :cp classpath          ;; from make-classpath, .cp file
     :main main-opts        ;; effective main opts, .main file
     :jvm jvm-opts          ;; effective jvm opts, .jvm file
     :trace trace-log       ;; from resolve-deps, if requested, trace.edn file

     ;; Intermediate/source data:
     :deps merged-deps      ;; effective merged :deps
     :paths local-paths     ;; from make-classpath, just effective local paths
     ;; and any other qualified keys from top level merged deps
    }"
  [{:keys [install-deps user-deps project-deps config-data ;; all deps.edn maps
           resolve-aliases makecp-aliases jvmopt-aliases main-aliases tool-aliases aliases
           skip-cp threads trace] :as opts}]
  (let [;; tool use - replace :deps / :paths if needed
        tool-args (deps/combine-aliases
                    (deps/merge-edns [install-deps user-deps project-deps config-data]) ;; merge just to get all aliases
                    (concat tool-aliases aliases))
        project-deps (deps/tool project-deps tool-args)

        ;; calc basis
        merge-edn (deps/merge-edns [install-deps user-deps project-deps config-data])
        _ (check-aliases merge-edn (concat resolve-aliases makecp-aliases jvmopt-aliases main-aliases tool-aliases aliases))
        resolve-args (cond-> (deps/combine-aliases merge-edn (concat resolve-aliases aliases))
                       threads (assoc :threads (Long/parseLong threads))
                       trace (assoc :trace trace))
        cp-args (deps/combine-aliases merge-edn (concat makecp-aliases aliases))
        basis (when-not skip-cp (deps/calc-basis merge-edn {:resolve-args resolve-args :classpath-args cp-args}))
        trace-log (-> basis :libs meta :trace)

        ;; handle jvm and main opts
        jvm (seq (get (deps/combine-aliases merge-edn (concat jvmopt-aliases aliases)) :jvm-opts))
        main (seq (get (deps/combine-aliases merge-edn (concat main-aliases aliases)) :main-opts))]
    (cond-> basis
      jvm (assoc :jvm jvm)
      main (assoc :main main))))

(defn read-deps
  [name]
  (when (not (str/blank? name))
    (let [f (jio/file name)]
      (when (.exists f)
        (reader/slurp-deps f)))))

(defn run
  "Run make-classpath script. See -main for details."
  [{:keys [config-user config-project libs-file cp-file jvm-file main-file basis-file skip-cp] :as opts}]
  (let [opts' (merge opts {:install-deps (reader/install-deps)
                           :user-deps (read-deps config-user)
                           :project-deps (read-deps config-project)})
        {:keys [libs classpath jvm main] :as basis} (run-core opts')
        trace (-> libs meta :trace)]
    (when trace
      (spit "trace.edn" (binding [*print-namespace-maps* false] (with-out-str (clojure.pprint/pprint trace)))))
    (when-not skip-cp
      (io/write-file libs-file (binding [*print-namespace-maps* false] (pr-str libs)))
      (io/write-file cp-file (-> classpath keys deps/join-classpath)))
    (io/write-file basis-file (binding [*print-namespace-maps* false] (pr-str basis)))
    (if jvm
      (io/write-file jvm-file (str/join " " jvm))
      (let [jf (jio/file jvm-file)]
        (when (.exists jf)
          (.delete jf))))
    (if main
      (io/write-file main-file (str/join " " main))
      (let [mf (jio/file main-file)]
        (when (.exists mf)
          (.delete mf))))))

(defn -main
  "Main entry point for make-classpath script.

  Options:
    --config-user=path - user deps.edn file (usually ~/.clojure/deps.edn)
    --config-project=path - project deps.edn file (usually ./deps.edn)
    --config-data={...} - deps.edn as data (from -Sdeps)
    --libs-file=path - libs cache file to write
    --cp-file=path - cp cache file to write
    --jvm-file=path - jvm opts file to write
    --main-file=path - main opts file to write
    --basis-file=path - basis file to write
    -Rresolve-aliases - concatenated resolve-deps alias names
    -Cmakecp-aliases - concatenated make-classpath alias names
    -Jjvmopt-aliases - concatenated jvm-opt alias names
    -Mmain-aliases - concatenated main-opt alias names
    -Ttool-aliases - concatenated tool alias names
    -Aaliases - concatenated generic alias names

  Resolves the dependencies and updates the lib, classpath, etc files.
  The libs file is at <cachedir>/<hash>.libs
  The cp file is at <cachedir>/<hash>.cp
  The main opts file is at <cachedir>/<hash>.main (if needed)
  The jvm opts file is at <cachedir>/<hash>.jvm (if needed)"
  [& args]
  (try
    (let [{:keys [options errors]} (parse-opts args)]
      (when (seq errors)
        (run! println errors)
        (System/exit 1))
      (run options))
    (catch Throwable t
      (printerrln "Error building classpath." (.getMessage t))
      (when-not (instance? IExceptionInfo t)
        (.printStackTrace t))
      (System/exit 1))))
