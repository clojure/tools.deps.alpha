;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.cli.api
  "This api provides functions that can be executed from the Clojure tools using -X:deps."
  (:require
    [clojure.java.io :as jio]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.tool :as tool]
    [clojure.tools.deps.alpha.tree :as tree]
    [clojure.tools.deps.alpha.script.resolve-tags :as resolve-tags]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.extensions.pom :as pom]
    [clojure.tools.deps.alpha.extensions.local :as local]
    [clojure.tools.deps.alpha.gen.pom :as gen-pom]
    [clojure.tools.deps.alpha.util.maven :as mvn]
    [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]])
  (:import
    [java.io File FileNotFoundException IOException]
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]
    [java.util.jar JarFile]
    [org.apache.maven.model Model]
    [org.eclipse.aether.artifact DefaultArtifact]
    [org.eclipse.aether.installation InstallRequest]
    [clojure.lang IExceptionInfo]))

(set! *warn-on-reflection* true)

(defn basis
  "Calculates and returns the runtime basis from a master deps edn map, modifying
   resolve-deps and make-classpath args as needed.

    master-edn - a master deps edn map
    args - an optional map of arguments to constituent steps, keys:
      :resolve-args - map of args to resolve-deps, with possible keys:
        :extra-deps
        :override-deps
        :default-deps
        :threads - number of threads to use during deps resolution
        :trace - flag to record a trace log
      :prep-args - map of args to prep-libs!
        :action - what to do when an unprepped lib is found, one of:
                    :prep - if unprepped, prep
                    :force - prep regardless of whether already prepped
                    :error (default) - don't prep, error
      :classpath-args - map of args to make-classpath-map, with possible keys:
        :extra-paths
        :classpath-overrides

  Returns {:basis basis}, which basis is initial deps edn map plus these keys:
    :resolve-args - the resolve args passed in, if any
    :classpath-args - the classpath args passed in, if any
    :libs - lib map, per resolve-deps
    :classpath - classpath map per make-classpath-map
    :classpath-roots - vector of paths in classpath order"
  [params]
  (merge params
    {:basis (deps/create-basis params)}))

(defn prep
  "Prep the unprepped libs found in the transitive lib set of basis.
  If no basis is provided, create and use the default project basis.

  Options:
    :basis - basis to prep. If not provided, use (create-basis nil).
    :force - flag on whether to force prepped libs to re-prep (default = false)
    :log - :none, :info (default), or :debug

  Returns params modified."
  [{:keys [basis force log] :or {log :info} :as params}]
  (let [use-basis (or basis (deps/create-basis nil))
        opts {:action (if force :force :prep)
              :log log}]
    (deps/prep-libs! (:libs use-basis) opts basis)
    params))

(comment
  (do
    (-> {:root {:mvn/repos mvn/standard-repos, :deps nil}
         :project {:deps '{org.clojure/clojure {:mvn/version "1.10.3"}
                           io.github.puredanger/cool-lib
                           {:git/sha "657d5ce88be340ab2a6c0befeae998366105be84"}}}
         :log :debug}
      basis
      prep)
    nil)
  )

(defn- make-trace
  []
  (let [{:keys [root-edn user-edn project-edn]} (deps/find-edn-maps)
        merged (deps/merge-edns [root-edn user-edn project-edn])
        basis (deps/calc-basis merged {:resolve-args {:trace true}})]
    (-> basis :libs meta :trace)))

(defn tree
  "Print deps tree for the current project's deps.edn built from either the
  current directory deps.edn, or if provided, the trace file.

  By default, :format will :print to the console in a human friendly tree. Use
  :edn mode to print the tree to edn.

  In print mode, deps are printed with prefix of either . (included) or X (excluded).
  A reason code for inclusion/exclusion may be added at the end of the line.

  Input options:
    :file      Path to trace.edn file (from clj -Strace) to use in computing the tree

  Output mode:
    :format    :print (default) or :edn

  Print output mode modifiers:
    :indent    Indent spacing (default = 2)
    :hide-libs Set of libs to hide as deps (if not top dep), default = #{org.clojure/clojure}"
  [opts]
  (try
    (let [{:keys [file format] :or {format :print}} opts
          trace (if file
                  (io/slurp-edn file)
                  (make-trace))
          tree (tree/trace->tree trace)]
      (case format
        :print (tree/print-tree tree opts)
        :edn (pprint/pprint tree)
        (throw (ex-info (str "Unknown format " format) {}))))
    (catch Throwable t
      (printerrln "Error generating tree:" (.getMessage t))
      (when-not (instance? IExceptionInfo t)
        (.printStackTrace t))
      (System/exit 1))))

;;;; git resolve-tags

(defn git-resolve-tags
  "Resolve git tags in deps.edn git deps to full shas."
  [_]
  (resolve-tags/exec {:deps-file "deps.edn"}))

;;;; Generate pom

(defn mvn-pom
  "Sync or create pom.xml from deps.edn.

  Options:
    :argmaps - vector of aliases to combine into argmaps to resolve-deps and make-classpath"
  [{:keys [argmaps]}]
  (try
    (let [{:keys [root-edn user-edn project-edn]} (deps/find-edn-maps)
          merged (deps/merge-edns [root-edn user-edn project-edn])
          args (deps/combine-aliases merged argmaps)
          basis (deps/calc-basis merged {:resolve-args args, :classpath-args args})
          ;; treat all transitive deps as top-level deps
          updated-deps (reduce-kv (fn [m lib {:keys [dependents] :as coord}]
                                    (if (seq dependents) m (assoc m lib coord)))
                         {} (:libs basis))]
      (gen-pom/sync-pom (merge basis {:deps updated-deps}) (jio/file ".")))
    (catch Throwable t
      (printerrln "Error generating pom manifest:" (.getMessage t))
      (when-not (instance? IExceptionInfo t)
        (.printStackTrace t))
      (System/exit 1))))

;;;; Install jar into local repository

(defn- read-pom-file
  [pom]
  (let [pom-file (jio/file pom)]
    (if (.exists pom-file)
      (let [^Model model (pom/read-model-file pom-file (deps/root-deps))]
        {:group-id (.getGroupId model)
         :artifact-id (.getArtifactId model)
         :version (.getVersion model)
         :pom-file pom})
      (throw (FileNotFoundException. (str "Pom file not found: " (str pom)))))))

(defn- gen-pom-file
  [lib version classifier]
  (let [group-id (namespace lib)
        artifact-id (name lib)
        temp-dir (.toString (Files/createTempDirectory "pom" (make-array FileAttribute 0)))
        pom-file (str temp-dir "/pom.xml")]
    (gen-pom/sync-pom {:params {:target-dir temp-dir
                                :src-pom pom-file
                                :lib lib
                                :version version}})
    {:group-id group-id
     :artifact-id artifact-id
     :version version
     :classifier classifier
     :pom-file pom-file}))

(defn- read-pom-in-jar
  [jar-name]
  (let [jar-file (jio/file jar-name)]
    (when (or (nil? jar-name) (not (.exists jar-file)))
      (throw (FileNotFoundException. (str "Jar file not found: " jar-name))))
    (let [jar (JarFile. jar-file)]
      (if-let [path (local/find-pom jar)]
        (let [entry (.getJarEntry jar path)
              jis (.getInputStream jar entry)
              tmp (File/createTempFile "pom" ".xml")]
          (jio/copy jis tmp)
          (read-pom-file tmp))
        (throw (IOException. (str "Jar file does not contain pom: " jar-name)))))))

(defn- output-path
  [local-repo group-id artifact-id version]
  (let [path-parts (concat
                     [(or local-repo @mvn/cached-local-repo)]
                     (str/split group-id #"\.")
                     [artifact-id version])]
    (.getAbsolutePath ^File (apply jio/file path-parts))))

(defn mvn-install
  "Install a jar and pom to the Maven local cache.
  The pom file must either be supplied, or generated based
  on provided lib/version/classifier, or provided inside the jar.
  The group/artifact/version coordinate will be pulled from the
  pom source as above.

  Required:
    :jar (reqired) - path to jar file (embedded pom used by default)

  Explicit pom options:
    :pom - path to pom file (pom in jar ignored)

  Generated pom options:
    :lib - qualified symbol like my.org/lib
    :version - string
    :classifier - string

  Other options:
    :local-repo (optional) - path to local repo (default = ~/.m2/repository)

  Execute ad-hoc:
    clj -X:deps mvn/install :jar '\"foo-1.2.3.jar\"'"
  [{:keys [jar pom lib version classifier local-repo] :as opts}]
  (println "Installing" jar (if pom (str "and " pom) ""))
  (let [{:keys [pom-file group-id artifact-id version classifier]}
        (cond
          pom (read-pom-file pom)
          lib (gen-pom-file lib version classifier)
          :else (read-pom-in-jar jar))
        jar-file (jio/file jar)
        pom-file (jio/file pom-file)
        system (mvn/make-system)
        session (mvn/make-session system (or local-repo @mvn/cached-local-repo))
        artifacts [(.setFile (DefaultArtifact. group-id artifact-id classifier "jar" version) jar-file)
                   (.setFile (DefaultArtifact. group-id artifact-id classifier "pom" version) pom-file)]
        install-request (.setArtifacts (InstallRequest.) artifacts)]
    (.install system session install-request)
    (println "Installed to" (output-path local-repo group-id artifact-id version))))

(defn find-versions
  "Find available tool versions given either a lib (with :lib) or
  existing installed tool (with :tool). If lib, check all registered
  procurers and print one coordinate per line when found."
  [{:keys [lib tool] :as args}]
  (let [{:keys [root-edn user-edn]} (deps/find-edn-maps)
        master-edn (deps/merge-edns [root-edn user-edn])]
    (cond
      tool
      (let [{:keys [lib coord]} (tool/resolve-tool (name tool))
            coord-type (ext/coord-type coord)
            coords (ext/find-versions lib coord coord-type master-edn)]
        (run! #(binding [*print-namespace-maps* false] (prn %)) coords))

      lib
      (let [coords (ext/find-all-versions lib {} master-edn)]
        (run! #(binding [*print-namespace-maps* false] (prn %)) coords))

      :else
      (throw (ex-info "Either :lib or :tool must be provided to find versions" (or args {}))))))

(comment
  (find-versions '{:lib org.clojure/tools.gitlibs})
  (find-versions '{:lib io.github.clojure/tools.gitlibs})
  (find-versions '{:tool tools})
  (find-versions nil)
  )