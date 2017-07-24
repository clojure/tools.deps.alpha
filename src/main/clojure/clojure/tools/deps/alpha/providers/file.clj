(ns clojure.tools.deps.alpha.providers.file
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as jio]
    [clojure.tools.deps.alpha.providers :as providers]))

;; TODO - this currently handles the "jar file" case, but doesn't handle
;; pointing to an actual project directory, which might have its own
;; deps.edn file and/or project.clj and/or pom.xml etc

(defmethod providers/expand-dep :file
  [lib {:keys [path] :as coord} provider]
  (assert (not (nil? path)) (format "File coordinate for %s is missing :path" lib))
  (let [path-file (jio/file path)]
    (if (.isDirectory path-file)
      (let [deps-file (jio/file path "deps.edn")]
        (when (.exists deps-file)
          (seq (:deps (-> deps-file slurp edn/read-string))))))))

(defmethod providers/download-dep :file
  [lib coord provider]
  coord)

