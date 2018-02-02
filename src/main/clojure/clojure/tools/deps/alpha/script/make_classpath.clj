;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.script.make-classpath
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.reader :as reader]
    [clojure.tools.deps.alpha.script.parse :as parse]
    [clojure.tools.deps.alpha.util.io :refer [printerrln]]
    [clojure.tools.cli :as cli]

    ;; Load extensions
    [clojure.tools.deps.alpha.extensions.maven]
    [clojure.tools.deps.alpha.extensions.local]
    [clojure.tools.deps.alpha.extensions.git]
    [clojure.tools.deps.alpha.extensions.deps]
    [clojure.tools.deps.alpha.extensions.pom])
  (:import
    [clojure.lang ExceptionInfo]))

(def ^:private opts
  [;; deps.edn inputs
   [nil "--config-files PATHS" "Comma delimited list of deps.edn files to merge" :parse-fn parse/parse-files]
   [nil "--config-data EDN" "Final deps.edn data to treat as the last deps.edn file" :parse-fn parse/parse-config]
   ;; output files
   [nil "--libs-file PATH" "Libs cache file to write"]
   [nil "--cp-file PATH" "Classpatch cache file to write"]
   [nil "--jvm-file PATH" "JVM options file"]
   [nil "--main-file PATH" "Main options file"]
   ;; aliases
   ["-R" "--resolve-aliases ALIASES" "Concatenated resolve-deps alias names" :parse-fn parse/parse-kws]
   ["-C" "--makecp-aliases ALIASES" "Concatenated make-classpath alias names" :parse-fn parse/parse-kws]
   ["-J" "--jvmopt-aliases ALIASES" "Concatenated jvm option alias names" :parse-fn parse/parse-kws]
   ["-M" "--main-aliases ALIASES" "Concatenated main option alias names" :parse-fn parse/parse-kws]
   ["-A" "--aliases ALIASES" "Concatenated generic alias names" :parse-fn parse/parse-kws]])

(defn run
  "Run make-classpath script. See -main for details."
  [{:keys [config-files config-data ;; input deps.edn data
           libs-file cp-file jvm-file main-file ;; output files
           resolve-aliases makecp-aliases jvmopt-aliases main-aliases aliases]
    :as opts}]
  (let [deps-map (reader/read-deps config-files)
        deps-map (if config-data (reader/merge-deps [deps-map config-data]) deps-map)
        resolve-args (deps/combine-aliases deps-map (concat aliases resolve-aliases))
        cp-args (deps/combine-aliases deps-map (concat aliases makecp-aliases))
        jvm-opts (get (deps/combine-aliases deps-map (concat aliases jvmopt-aliases)) :jvm-opts)
        main-opts (get (deps/combine-aliases deps-map (concat aliases main-aliases)) :main-opts)
        libs (deps/resolve-deps deps-map resolve-args)
        cp (deps/make-classpath libs (:paths deps-map) cp-args)]
    (jio/make-parents libs-file)
    (spit libs-file (pr-str libs))
    (jio/make-parents cp-file)
    (spit cp-file cp)
    (when (seq jvm-opts)
      (jio/make-parents jvm-file)
      (spit jvm-file (str/join " " jvm-opts)))
    (when (seq main-opts)
      (jio/make-parents main-file)
      (spit main-file (str/join " " main-opts)))))

(comment
  (get (deps/combine-aliases {:aliases {:j {:jvm-opts ["-Dfoo" "-Xmx500m"]}}} (concat nil [:j])) :jvm-opts)
  )

(defn -main
  "Main entry point for make-classpath script.

  Options:
    --config-files=/install/deps.edn,... - comma-delimited list of deps.edn files to merge
    --config-data={...} - deps.edn as data
    --libs-file=path - libs cache file to write
    --cp-file=path - cp cache file to write
    --jvm-file=path - jvm opts file to write
    --main-file=path - main opts file to write
    -Rresolve-aliases - concatenated resolve-deps alias names
    -Cmakecp-aliases - concatenated make-classpath alias names
    -Jjvmopt-aliases - concatenated jvm-opt alias names
    -Mmain-aliases - concatenated main-opt alias names
    -Aaliases - concatenated generic alias names

  Resolves the dependencies and updates the lib, classpath, etc files.
  The libs file is at <cachedir>/<resolve-aliases>.libs
  The cp file is at <cachedir>/<resolve-aliases>/<cpaliases>.cp"
  [& args]
  (try
    (let [{:keys [options errors]} (cli/parse-opts args opts)]
      (when (seq errors)
        (run! println errors)
        (System/exit 1))
      (run options))
    (catch Throwable t
      (printerrln "Error building classpath." (.getMessage t))
      (when-not (instance? ExceptionInfo t)
        (.printStackTrace t))
      (System/exit 1))))

(comment
  (def user-deps (jio/file (System/getProperty "user.home") ".clojure" "deps.edn"))
  (run
    {:config-files [(jio/file (str (.getAbsolutePath user-deps))) (jio/file "deps.edn")]
     :libs-file "foo.libs"
     :cp-file "foo.cp"})

  (run
    {:config-files [(jio/file (str (.getAbsolutePath user-deps))) (jio/file "deps.edn")]
     :libs-file "foo.libs"
     :cp-file "foo.cp"
     :config-data {:deps {'org.clojure/test.check {:mvn/version "0.9.0"}}}})

  (run
    {:config-data {:aliases {:j1 {:jvm-opts ["-Xms100m" "-Xmx200m"]}
                             :j2 {:jvm-opts ["-server"]}}}
     :aliases [:j1 :j2]
     :libs-file "foo.libs"
     :cp-file "foo.cp"
     :jvm-file "foo.jvm"
     :main-file "foo.main"})
  )
