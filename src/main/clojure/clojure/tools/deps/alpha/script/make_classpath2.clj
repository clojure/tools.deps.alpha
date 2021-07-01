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
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.tool :as tool]
    [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]]
    [clojure.tools.deps.alpha.script.parse :as parse]
    [clojure.tools.deps.alpha.tree :as tree])
  (:import
    [clojure.lang IExceptionInfo]))

(def ^:private opts
  [;; deps.edn inputs
   [nil "--config-user PATH" "User deps.edn location"]
   [nil "--config-project PATH" "Project deps.edn location"]
   [nil "--config-data EDN" "Final deps.edn data to treat as the last deps.edn file" :parse-fn parse/parse-config]
   ;; tool args to resolve
   [nil "--tool-mode" "Tool mode (-T), may optionally supply tool-name or tool-aliases"]
   [nil "--tool-name NAME" "Tool name"]
   [nil "--function FUNCTION" "Tool function to resolve" :parse-fn symbol]
   ;; output files
   [nil "--libs-file PATH" "Libs cache file to write"]
   [nil "--cp-file PATH" "Classpatch cache file to write"]
   [nil "--jvm-file PATH" "JVM options file"]
   [nil "--main-file PATH" "Main options file"]
   [nil "--basis-file PATH" "Basis file"]
   [nil "--function-file PATH" "Function file"]
   [nil "--skip-cp" "Skip writing .cp and .libs files"]
   ;; aliases
   ["-R" "--resolve-aliases ALIASES" "Concatenated resolve-deps alias names" :parse-fn parse/parse-kws]
   ["-C" "--makecp-aliases ALIASES" "Concatenated make-classpath alias names" :parse-fn parse/parse-kws]
   ["-A" "--repl-aliases ALIASES" "Concatenated repl alias names" :parse-fn parse/parse-kws]
   ["-M" "--main-aliases ALIASES" "Concatenated main option alias names" :parse-fn parse/parse-kws]
   ["-X" "--exec-aliases ALIASES" "Concatenated exec alias names" :parse-fn parse/parse-kws]
   ["-T" "--tool-aliases ALIASES" "Concatenated tool alias names" :parse-fn parse/parse-kws]
   ;; options
   [nil "--trace" "Emit trace log to trace.edn"]
   [nil "--threads THREADS" "Threads for concurrent downloads"]
   [nil "--tree" "Print deps tree to console"]])

(defn parse-opts
  "Parse the command line opts to make-classpath"
  [args]
  (cli/parse-opts args opts))

(defn check-aliases
  "Check that all aliases are known and warn if aliases are undeclared"
  [deps aliases]
  (when-let [unknown (seq (remove #(contains? (:aliases deps) %) (distinct aliases)))]
    (printerrln "WARNING: Specified aliases are undeclared and are not being used:" (vec unknown))))

(defn resolve-tool-args
  "Resolves the tool by name to the coord + usage data.
   Create the proper args to include the lib/coord for the tool
   and the resolved f in terms of usage data."
  [tool-data function config]
  (let [{:keys [lib coord]} tool-data
        manifest-type (ext/manifest-type lib coord config)
        coord' (merge coord manifest-type)
        {:keys [ns-default ns-aliases]} (ext/coord-usage lib coord' (:deps/manifest coord') config)
        resolved-function (let [fns (when-let [nss (namespace function)] (symbol nss))
                                fn (symbol (name function))]
                            (if fns
                              (if-let [aliased-ns (get ns-aliases fns)]
                                (symbol (str aliased-ns) (str fn))
                                function)
                              (if ns-default
                                (symbol (str ns-default) (str fn))
                                function)))]
    {:tool-args {:replace-deps {lib coord'}
                 :replace-paths ["."]}
     :resolved-function resolved-function}))

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
           tool-mode tool-name function tool-resolver ;; -T options
           resolve-aliases makecp-aliases main-aliases exec-aliases repl-aliases tool-aliases
           skip-cp threads trace tree] :as _opts}]
  (when (and main-aliases exec-aliases)
    (throw (ex-info "-M and -X cannot be used at the same time" {})))
  (let [pretool-edn (deps/merge-edns [install-deps user-deps project-deps config-data])
        ;; tool use - :deps/:paths/:replace-deps/:replace-paths in project if needed
        {:keys [resolved-function tool-args]} (cond
                                                tool-name (resolve-tool-args (tool-resolver tool-name) function pretool-edn)
                                                tool-mode {:tool-args {:replace-deps {} :replace-paths ["."]}
                                                           :resolved-function function})
        ;; :deps/TOOL is used only here to inject the tool's alias config into the tool args
        combined-tool-args (deps/combine-aliases
                            (deps/merge-edns [pretool-edn (when tool-args {:aliases {:deps/TOOL tool-args}})])
                            (concat main-aliases exec-aliases repl-aliases tool-aliases (when tool-args [:deps/TOOL])))
        project-deps (deps/tool project-deps combined-tool-args)

        ;; calc basis
        merge-edn (deps/merge-edns [install-deps user-deps project-deps config-data]) ;; recalc to get updated project-deps
        combined-exec-aliases (concat main-aliases exec-aliases repl-aliases tool-aliases)
        _ (check-aliases merge-edn (concat resolve-aliases makecp-aliases combined-exec-aliases))
        resolve-argmap (deps/combine-aliases merge-edn (concat resolve-aliases combined-exec-aliases))
        resolve-args (cond-> resolve-argmap
                       threads (assoc :threads (Long/parseLong threads))
                       trace (assoc :trace trace)
                       tree (assoc :trace true))
        basis (when-not skip-cp (deps/calc-basis merge-edn {:resolve-args resolve-args
                                                            :classpath-args (deps/combine-aliases merge-edn
                                                                              (concat makecp-aliases combined-exec-aliases))}))

        ;; check for unprepped libs
        _ (deps/prep-libs! (:libs basis) {:action :error} basis)

        ;; handle jvm and main opts
        exec-argmap (deps/combine-aliases merge-edn combined-exec-aliases)
        jvm (seq (get exec-argmap :jvm-opts))
        main (seq (get exec-argmap :main-opts))]
    (when (and main repl-aliases)
      (io/printerrln "WARNING: Use of :main-opts with -A is deprecated. Use -M instead."))
    (cond-> basis
      jvm (assoc :jvm jvm)
      ;; FUTURE: narrow this to (and main main-aliases)
      main (assoc :main main)
      resolved-function (assoc :resolved-function resolved-function))))

(defn read-deps
  [name]
  (when (not (str/blank? name))
    (let [f (jio/file name)]
      (when (.exists f)
        (deps/slurp-deps f)))))

(defn run
  "Run make-classpath script. See -main for details."
  [{:keys [config-user config-project libs-file cp-file jvm-file main-file basis-file function-file skip-cp trace tree] :as opts}]
  (let [opts' (merge opts {:install-deps (deps/root-deps)
                           :user-deps (read-deps config-user)
                           :project-deps (read-deps config-project)
                           :tool-resolver tool/resolve-tool})
        {:keys [libs classpath-roots jvm main resolved-function] :as basis} (run-core opts')
        trace-log (-> libs meta :trace)]
    (when trace
      (spit "trace.edn" (binding [*print-namespace-maps* false] (with-out-str (clojure.pprint/pprint trace-log)))))
    (when tree
      (-> trace-log tree/trace->tree (tree/print-tree nil)))
    (when-not skip-cp
      (io/write-file libs-file (binding [*print-namespace-maps* false] (pr-str libs)))
      (io/write-file cp-file (-> classpath-roots deps/join-classpath)))
    (io/write-file basis-file (binding [*print-namespace-maps* false] (pr-str basis)))
    (if jvm
      (io/write-file jvm-file (apply str (interleave jvm (repeat "\n"))))
      (let [jf (jio/file jvm-file)]
        (when (.exists jf)
          (.delete jf))))
    (if main
      (io/write-file main-file (apply str (interleave main (repeat "\n"))))
      (let [mf (jio/file main-file)]
        (when (.exists mf)
          (.delete mf))))
    (if resolved-function
      (io/write-file function-file (str resolved-function)))))

(defn -main
  "Main entry point for make-classpath script.

  Options:
    --config-user=path - user deps.edn file (usually ~/.clojure/deps.edn)
    --config-project=path - project deps.edn file (usually ./deps.edn)
    --config-data={...} - deps.edn as data (from -Sdeps)
    --function=function - function symbol
    --function-file=path - function cache file to write
    --tool-mode - flag for tool mode
    --tool-name - name of tool to run
    --libs-file=path - libs cache file to write
    --cp-file=path - cp cache file to write
    --jvm-file=path - jvm opts file to write
    --main-file=path - main opts file to write
    --basis-file=path - basis file to write
    -Rresolve-aliases - concatenated resolve-deps alias names
    -Cmakecp-aliases - concatenated make-classpath alias names
    -Mmain-aliases - concatenated main-opt alias names
    -Aaliases - concatenated repl alias names
    -Xaliases - concatenated exec alias names
    -Taliases - concatenated tool alias names

  Resolves the dependencies and updates the lib, classpath, etc files.
  The libs file is at <cachedir>/<hash>.libs
  The cp file is at <cachedir>/<hash>.cp
  The main opts file is at <cachedir>/<hash>.main (if needed)
  The jvm opts file is at <cachedir>/<hash>.jvm (if needed)
  The function file is at <cachedir>/<hash>.function (if needed)"
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
