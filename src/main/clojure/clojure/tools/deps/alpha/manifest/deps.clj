(ns clojure.tools.deps.alpha.manifest.deps
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.deps.alpha.providers :as providers]
    [clojure.tools.deps.alpha.reader :as reader]))

(defn- deps-map
  [dir]
  (reader/read-deps [(jio/file dir "deps.edn")]))

(defmethod providers/coord-deps :deps
  [_lib {:keys [deps/root] :as coord} _mf _config]
  (seq (:deps (deps-map root))))

(defmethod providers/coord-paths :deps
  [_lib {:keys [deps/root] :as coord} _mf _config]
  (into []
    (map #(.getAbsolutePath (jio/file root %)))
    (:paths (deps-map root))))

