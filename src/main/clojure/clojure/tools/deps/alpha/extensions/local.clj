;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.alpha.extensions.local
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.extensions.pom :as pom]
    [clojure.tools.deps.alpha.util.dir :as dir])
  (:import
    [java.io File IOException]
    [java.net URL]
    [java.util.jar JarFile JarEntry]
    ;; maven-builder-support
    [org.apache.maven.model.building UrlModelSource]))

(defmethod ext/dep-id :local
  [lib {:keys [local/root] :as _coord} _config]
  {:lib lib
   :root root})

(defmethod ext/canonicalize :local
  [lib {:keys [local/root] :as coord} _config]
  [lib (assoc coord :local/root (.getCanonicalPath (dir/canonicalize (jio/file root))))])

(defmethod ext/lib-location :local
  [_lib {:keys [local/root]} _config]
  {:base root
   :path ""
   :type :local})

(defmethod ext/manifest-type :local
  [_lib {:keys [local/root deps/manifest] :as _coord} _config]
  (cond
    manifest {:deps/manifest manifest :deps/root root}
    (.isFile (jio/file root)) {:deps/manifest :jar, :deps/root root}
    :else (ext/detect-manifest root)))

(defmethod ext/coord-summary :local [lib {:keys [local/root]}]
  (str lib " " root))

(defn find-pom
  "Find path of pom file in jar file, or nil if it doesn't exist"
  [^JarFile jar-file]
  (try
    (loop [[^JarEntry entry & entries] (enumeration-seq (.entries jar-file))]
      (when entry
        (let [name (.getName entry)]
          (if (and (str/starts-with? name "META-INF/")
                (str/ends-with? name "pom.xml"))
            name
            (recur entries)))))
    (catch IOException _t nil)))

(defmethod ext/coord-deps :jar
  [_lib {:keys [local/root] :as _coord} _manifest config]
  (let [jar (JarFile. (jio/file root))]
    (if-let [path (find-pom jar)]
      (let [url (URL. (str "jar:file:" root "!/" path))
            src (UrlModelSource. url)
            model (pom/read-model src config)]
        (pom/model-deps model))
      [])))

(defmethod ext/coord-paths :jar
  [_lib coord _manifest _config]
  [(:local/root coord)])
