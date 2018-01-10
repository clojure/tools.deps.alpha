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
    [clojure.tools.deps.alpha.extensions.deps]))

(def ^:private opts
  [[nil "--config-files PATHS" "Comma delimited list of deps.edn files to merge" :parse-fn parse/parse-files]
   [nil "--libs-file PATH" "Libs cache file to write"]
   [nil "--cp-file PATH" "Classpatch cache file to write"]
   ["-R" "--resolve-aliases ALIASES" "Concatenated resolve-deps alias names" :parse-fn parse/parse-kws]
   ["-C" "--makecp-aliases ALIASES" "Concatenated make-classpath alias names" :parse-fn parse/parse-kws]])

(defn -main
  "Main entry point for make-classpath script.

  Required:
    --config-files=/install/deps.edn,... - comma-delimited list of deps.edn files to merge
    --libs-file=path - libs cache file to write
    --cp-file=path - cp cache file to write
  Options:
    -Rresolve-aliases - concatenated resolve-deps alias names
    -Cmakecp-aliases - concatenated make-classpath alias names

  Resolves the dependencies and updates the cached libs and/or classpath file.
  The libs file is at <cachedir>/<resolve-aliases>.libs
  The cp file is at <cachedir>/<resolve-aliases>/<cpaliases>.cp"
  [& args]
  (try
    (let [{:keys [options errors]} (cli/parse-opts args opts)]
      (when (seq errors)
        (run! println errors)
        (System/exit 1))

      (let [{:keys [config-files libs-file cp-file resolve-aliases makecp-aliases]} options
            deps-map (reader/read-deps config-files)
            libs (let [resolve-args (deps/combine-aliases deps-map resolve-aliases)
                       libs (deps/resolve-deps deps-map resolve-args)]
                   (jio/make-parents libs-file)
                   (spit libs-file (pr-str libs))
                   libs)
            cp-args (deps/combine-aliases deps-map makecp-aliases)
            cp (deps/make-classpath libs (:paths deps-map) cp-args)]
        (jio/make-parents cp-file)
        (spit cp-file cp)))
    (catch Throwable t
      (printerrln "Error building classpath." (.getMessage t))
      (System/exit 1))))

(comment
  (def user-deps (jio/file (System/getProperty "user.home") ".clojure" "deps.edn"))
  (-main
    "--config-files" (str (.getAbsolutePath user-deps) "," "deps.edn")
    "--libs-file" "foo.libs"
    "--cp-file" "foo.cp")
  )
