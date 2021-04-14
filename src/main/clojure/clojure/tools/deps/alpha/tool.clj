;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.tool
  (:require
    [clojure.java.io :as jio]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.util.io :as io])
  (:import
    [java.io File InputStreamReader]
    [java.util.jar JarFile]))

(defn- tool-dir
  ^File []
  (jio/file (.getParentFile (jio/file (deps/user-deps-path))) "tools"))

(defn- tool-file
  "Create File location for tool name"
  ^File [tool]
  (jio/file (tool-dir) (str tool ".edn")))

(defn- read-usage
  "Read usage.edn from paths, which is a coll of either a directory or a jar file.
  If a directory, read from dir/usage.edn. If a jar, read from usage.edn at the root
  of the jar. The first usage.edn found is slurped and returned as edn. If none found,
  nil is returned."
  [paths]
  (loop [[path & ps] paths]
    (when path
      (let [f (jio/file path)]
        (cond
          (.isDirectory f)
          (let [usage-file (jio/file f "usage.edn")]
            (if (.exists usage-file)
              (io/slurp-edn usage-file)
              (recur ps)))

          (and (.exists f) (str/ends-with? path ".jar"))
          (let [jar (JarFile. f)]
            (let [entry (.getJarEntry jar "usage.edn")]
              (if entry
                (io/read-edn (InputStreamReader. (.getInputStream jar entry)))
                (recur ps))))

          :else
          (recur ps))))))

(defn install-tool
  "Procure the lib+coord, find it's usage.edn if any, install the tool to the user
  tools dir (records lib, coord, usage)."
  [lib coord as]
  (let [{:keys [root-edn user-edn]} (deps/find-edn-maps)
        master-edn (deps/merge-edns [root-edn user-edn])
        coord' (merge coord (ext/manifest-type lib coord master-edn))
        paths (ext/coord-paths lib coord' (:deps/manifest coord') master-edn)
        usage (read-usage paths)
        f (tool-file as)]
    (.mkdirs (.getParentFile f))
    (spit f
      (with-out-str
        (binding [*print-namespace-maps* false
                  pprint/*print-right-margin* 100]
          (pprint/pprint (cond-> {:lib lib :coord (dissoc coord' :deps/root :deps/manifest)}
                           usage (assoc :usage usage))))))))

(comment
  (binding [*print-namespace-maps* false]
    (with-out-str
      (pprint/pprint {:git/url "abc" :git/sha "123"})))

  (with-out-str
    (binding [*print-namespace-maps* false]
      (pprint/pprint {:git/url "abc" :git/sha "123"})))

  )

(defn resolve-tool
  "Resolve a tool by name, look up and return:
  {:lib lib
   :coord coord
   :usage usage-edn}
  Or nil if unknown."
  [tool]
  (let [f (tool-file tool)]
    (when (.exists f)
      (io/slurp-edn f))))

(defn list-tools
  "Return seq of available tool names"
  []
  (->> (.listFiles (tool-dir))
    (filter #(.isFile ^File %))
    (map #(.getName ^File %))
    (filter #(str/ends-with? % ".edn"))
    (map #(subs % 0 (- (count %) 4)))
    sort))

(defn remove-tool
  "Removes tool installation, if it exists. Returns true if it exists and was deleted."
  [tool]
  (let [f (tool-file tool)]
    (when (.exists f)
      (.delete f))))