;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.makecp
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.providers.maven]
            [clojure.tools.deps.alpha.providers.file]
            [clojure.string :as str])
  (:import [java.io File IOException FileReader PushbackReader]
           [java.nio.file Files LinkOption]
           [java.nio.file.attribute FileTime]))

(set! *warn-on-reflection* true)

(defn- ensure-dir
  "Asserts that the specified directory either exists, creating if needed
  and returning the directory File."
  [path]
  (let [f (jio/file path)]
    (when (not (.exists f))
      (.mkdirs f))
    f))

(defn- last-modified
  ^FileTime [^File f]
  (Files/getLastModifiedTime (.toPath f) ^"[Ljava.nio.file.LinkOption;" (make-array LinkOption 0)))

(defn- newer-than
  [^File f1 ^File f2]
  (if (.exists f1)
    (if (.exists f2)
      (pos? (.compareTo (last-modified f1) (last-modified f2)))
      true)
    false))

(defn- parse-opt
  "Turn opt like `-R:foo:bar` into {:R \":foo:bar\"}."
  [opt]
  {(keyword (subs opt 1 2)) (subs opt 2)})

(defn- parse-arg
  [parsed arg]
  (cond
    ;; comma-delimited list of config paths -> split, turn into files, keep only existing
    (str/starts-with? arg "--config-paths=")
    (let [paths (str/split (subs arg (count "--config-paths=")) #",")]
      (assoc parsed :config-files (->> paths (map jio/file) (filter #(.exists ^File %)))))

    (str/starts-with? arg "--cache-path=")
    (assoc parsed :cache-dir (ensure-dir (subs arg (count "--cache-path="))))

    :else
    (merge parsed (parse-opt arg))))

(defn- parse-args
  [args]
  (->> args (remove nil?) (reduce parse-arg {})))

(defn- read-kws
  [s]
  (->> (str/split (or s "") #":")
    (remove str/blank?)
    (map
      #(if-let [i (str/index-of % \/)]
         (keyword (subs % 0 i) (subs % (inc i)))
         (keyword %)))))

(defn- io-err
  ^IOException [fmt ^File f]
  (IOException. (format fmt (.getAbsolutePath f))))

(defn- slurp-edn-map
  "Read the file specified by the path-segments, slurp it, and read it as edn."
  [^File f]
  (let [EOF (Object.)]
    (with-open [rdr (PushbackReader. (FileReader. f))]
      (let [val (edn/read {:eof EOF} rdr)]
        (cond
          (identical? val EOF) nil ;; empty file
          (map? val) val
          :else (throw (io-err "Expected edn map: %s" f)))))))

(defn- read-deps
  "Read a set of user deps and merge them left to right into a single deps map."
  [deps-files]
  (let [configs (map slurp-edn-map deps-files)
        combined (apply merge-with merge configs)
        paths (last (->> configs (map :paths) (remove nil?)))]
    (assoc combined :paths paths)))

(defn- make-libs
  "If libs file is out of date, use deps and resolve-opt to form resolve-args, then
  run resolve-deps and cache. In either case, return the edn representation of the lib map."
  [deps refresh? ^File libs-file resolve-opt]
  (if refresh?
    ;; read deps, parse resolve opt, run resolve-deps, cache libs file
    (let [resolve-aliases (read-kws resolve-opt)
          resolve-args (apply merge-with merge (map #(get-in deps [:aliases %]) resolve-aliases))
          libs (deps/resolve-deps deps resolve-args)]
      (spit libs-file (pr-str libs))
      libs)
    (slurp-edn-map libs-file)))

(defn- make-cp
  "Use aliases and overrides to invoke make-classpath on the libs. If not using
  overrides, write to cp cache file."
  [deps libs cp-file cp-opt overrides-opt]
  (let [cp-aliases (read-kws cp-opt)
        overrides (reduce #(let [[lib path] (str/split %2 #"=")]
                             (assoc %1 (symbol lib) path))
                    {} (when overrides-opt (str/split overrides-opt #",")))
        cp-args (apply merge-with merge (conj (map #(get-in deps [:aliases %]) cp-aliases)
                                          {:classpath-overrides overrides}))
        cp (deps/make-classpath libs (:paths deps) cp-args)]
    (jio/make-parents cp-file)
    (spit cp-file cp)))

(defn -main
  "Main entry point for makecp script.

  Required:
    --config-paths=/install/deps.edn,... - comma-delimited list of deps.edn files to merge
    --cache-path=path - classpath cache directory
  Options:
    -Rresolve-aliases - concatenated resolve-args alias names
    -Cmake-classpath-aliases - concatenated make-classpath alias names
    -Pclasspath-overrides - comma-delimited lib to path overrides

  Resolves the dependencies and updates the cached libs and/or classpath file.
  The libs file is at <cachedir>/<resolve-aliases>.libs
  The cp file is at <cachedir>/<resolve-aliases>/<cpaliases>.cp"
  [& args]
  (try
    (let [{:keys [config-files cache-dir R C P]} (parse-args args)
          lib-path (or R "default")
          libs-file (jio/file cache-dir (str lib-path ".libs"))
          cp-file (jio/file cache-dir lib-path (str (or C "default") ".cp"))
          deps (read-deps config-files)
          libs (make-libs deps (newer-than (last config-files) libs-file) libs-file R)]
      (make-cp deps libs cp-file C P)
      nil)

    ;; print any exception message to stderr
    (catch Throwable t
      (binding [*out* *err*]
        (println "Error building classpath." (.getMessage t)))
      (System/exit 1))))

(comment
  (def clojure (str (System/getProperty "user.home") "/.clojure"))

  ;; write libmap to ./cp/default.libs and classpath to ./cp/default/default.cp
  ;; deps.edn = {:deps {org.clojure/clojure {:type :mvn :version "1.8.0"}, org.clojure/core.memoize {:type :mvn :version "0.5.8"}}}
  (-main
    (str "--config-paths=" clojure "/deps.edn" "," "deps.edn")
    (str "--cache-path=" ".cpcache"))

  (-main
    (str "--config-paths=" clojure "/deps.edn" "," "deps.edn")
    (str "--cache-path=" ".cpcache")
    "-R:perf")

  ;; no local deps, just use the user one
  (-main
    (str "--config-paths=" clojure "/deps.edn")
    (str "--cache-path=" clojure "/.cpcache"))
  )
