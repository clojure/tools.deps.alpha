(ns clojure.tools.deps.alpha.reader
  (:require [clojure.edn :as edn])
  (:import [java.io File IOException FileReader PushbackReader]))

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

(defn read-deps
  "Read a set of user deps.edn files and merge them left to right into a single deps map."
  [deps-files]
  (let [configs (map slurp-edn-map deps-files)
        combined (apply merge-with merge configs)
        paths (last (->> configs (map :paths) (remove nil?)))]
    (assoc combined :paths paths)))

