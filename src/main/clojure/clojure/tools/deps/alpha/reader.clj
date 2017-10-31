;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.reader
  (:require [clojure.edn :as edn]
            [clojure.walk :as walk]
            [clojure.tools.deps.alpha.util.coll :as coll])
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

(defn- canonicalize-sym [s]
  (if (and (symbol? s) (nil? (namespace s)))
    (as-> (name s) n (symbol n n))
    s))

(defn- canonicalize-all-syms
  [deps-map]
  (walk/postwalk
    #(cond-> % (map? %) (coll/map-keys canonicalize-sym))
    deps-map))

(defn read-deps
  "Read a set of user deps.edn files and merge them left to right into a single deps map."
  [deps-files]
  (let [configs (->> deps-files (map slurp-edn-map) (map canonicalize-all-syms))
        combined (apply merge-with merge configs)
        paths (last (->> configs (map :paths) (remove nil?)))]
    (assoc combined :paths paths)))

