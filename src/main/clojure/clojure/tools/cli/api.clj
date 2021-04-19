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
    [clojure.tools.deps.alpha.extensions.git :as git]
    [clojure.tools.deps.alpha.gen.pom :as gen-pom]
    [clojure.tools.deps.alpha.util.maven :as mvn]
    [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]]
    [clojure.tools.gitlibs :as gitlibs])
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
                     [(or local-repo mvn/default-local-repo)]
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
        session (mvn/make-session system (or local-repo mvn/default-local-repo))
        artifacts [(.setFile (DefaultArtifact. group-id artifact-id classifier "jar" version) jar-file)
                   (.setFile (DefaultArtifact. group-id artifact-id classifier "pom" version) pom-file)]
        install-request (.setArtifacts (InstallRequest.) artifacts)]
    (.install system session install-request)
    (println "Installed to" (output-path local-repo group-id artifact-id version))))

;;;; Tools

(defn install-tool
  "Install a tool for later use, taking the following required fields:
    lib - lib name, and coord value
    :as - tool name
  On install, the tool is procured, and persisted with the tool name for later use."
  [{:keys [as] :as args}]
  (let [lib (first (filter qualified-symbol? (keys args)))
        coord (get args lib)]
    (when (or (not lib) (not coord) (not as))
      (println "Missing required args: lib to coord or :as"))
    (let [{:keys [root-edn user-edn]} (deps/find-edn-maps)
          master-edn (deps/merge-edns [root-edn user-edn])
          coord (let [{:git/keys [url sha tag]} coord
                      url (if (nil? url) (git/auto-git-url lib) url)]
                  (cond-> coord
                    (and tag (nil? sha))
                    (assoc :git/sha (gitlibs/resolve url tag))))
          [lib coord] (ext/canonicalize lib coord master-edn)]
      (when-not (and lib coord)
        (throw (ex-info (format "Could not resolve tool: %s" (pr-str args)) args)))
      (tool/install-tool lib coord as)
      (println "Installed" as))))

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
        (run! #(binding [*print-namespace-maps* false] (pr-str %)) coords))

      lib
      (let [coords (ext/find-all-versions lib {} master-edn)]
        (run! #(binding [*print-namespace-maps* false] (pr-str %)) coords))

      :else
      (throw (ex-info "Either :lib or :tool must be provided to find versions" args)))))

(defn tool-info
  "If no args given, list available tools. Specify particular :tool to get more info about the tool."
  [{:keys [tool] :as args}]
  (if tool
    (let [{:keys [lib coord] :as info} (tool/resolve-tool tool)]
      (if info
        (do
          (println "Info for" tool ":")
          (println)
          (println "lib:" lib)
          (println "coord:")
          (binding [*print-namespace-maps* false]
            (pprint/pprint coord)))
        (println "Tool not found")))
    (run! #(println (str %)) (tool/list-tools))))

(defn tool-remove
  "Remove tool, if it exists."
  [{:keys [tool] :as args}]
  (if tool
    (if (tool/remove-tool tool)
      (println "Tool removed")
      (println "Tool not found or could not be removed"))))

(comment
  (find-versions '{:mvn/lib org.clojure/tools.gitlibs})
  (find-versions '{:git/lib io.github.clojure/tools.gitlibs})

  (install-tool '{io.github.seancorfield/clj-new
                  {:git/tag "v1.1.243"} :as "clj-new"})
  )