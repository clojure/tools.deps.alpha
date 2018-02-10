;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.extensions
  (:require [clojure.java.io :as jio]))

;; Helper for autodetect of manifest type

;; vector to control ordering
(def manifest-types
  ["deps.edn" :deps,
   "pom.xml" :pom
   ;; "project.clj" :lein
   ])

(defn detect-manifest
  "Given a directory, detect the manifest type and return the manifest info."
  [dir]
  (loop [[file-name manifest-type & others] manifest-types]
    (when file-name
      (let [f (jio/file dir file-name)]
        (if (and (.exists f) (.isFile f))
          {:deps/manifest manifest-type, :deps/root dir}
          (recur others))))))

;; Methods switching on coordinate type
(defn coord-type
  "The namespace (as a keyword) of the only qualified key in the coordinate,
   excluding the reserved deps namespace."
  [coord]
  (when (map? coord)
    (->> coord keys (keep namespace) (remove #(= "deps" %)) first keyword)))

(defmulti lib-location
  "Takes a coordinate and returns the location where the lib would be
  installed locally. Location keys:

  :base     local repo base directory path
  :path     path within base dir
  :type     coordinate type"
  (fn [lib coord config] (coord-type coord)))

(defmulti canonicalize
  "Takes a lib and coordinate and returns a canonical form.
  For example, a Maven coordinate might resolve LATEST to a version or a Git
  coordinate might resolve a partial sha to a full sha. Returns [lib coord]."
  (fn [lib coord config] (coord-type coord)))

(defmethod canonicalize :default [lib coord config]
  [lib coord])

(defmulti dep-id
  "Returns an identifier value that can be used to detect a lib/coord cycle while
   expanding deps. This will only be called after canonicalization so it can rely
   on the canonical form."
  (fn [lib coord config] (coord-type coord)))

(defn- throw-bad-coord
  [lib coord]
  (throw (ex-info (str "Coordinate type " (coord-type coord) " not loaded for library " lib " in coordinate " (pr-str coord))
           {:lib lib :coord coord})))

(defmethod dep-id :default [lib coord config]
  (throw-bad-coord lib coord))

(defmulti manifest-type
  "Takes a lib, a coord, and the root config. Dispatch based on the
   coordinate type. Detect and return the manifest type and location
   for this coordinate."
  (fn [lib coord config] (coord-type coord)))

(defmethod manifest-type :default [lib coord config]
  (throw-bad-coord lib coord))

(defmulti coord-summary
  "Takes a coord, and returns a concise description, used when printing tree"
  (fn [lib coord] (coord-type coord)))

(defmethod coord-summary :default [lib coord]
  (str lib " " (coord-type coord)))

;; Version comparison, either within or across coordinate types

(defmulti compare-versions
  "Given two coordinates, use this as a comparator returning a negative number, zero,
  or positive number when coord-x is logically 'less than', 'equal to', or 'greater than'
  coord-y. The dispatch occurs on the type of x and y."
  (fn [lib coord-x coord-y config] [(coord-type coord-x) (coord-type coord-y)]))

(defmethod compare-versions :default
  [lib coord-x coord-y config]
  (throw (ex-info (str "Unable to compare versions for " lib ": " (pr-str coord-x) " and " (pr-str coord-y))
           {:lib lib :coord-x coord-x :coord-y coord-y})))

;; Methods switching on manifest type

(defn- throw-bad-manifest
  [lib coord manifest-type]
  (if manifest-type
    (throw (ex-info (str "Manifest type " manifest-type " not loaded when finding deps for " lib " in coordinate " (pr-str coord))
             {:lib lib :coord coord}))
    (throw (ex-info (str "Manifest type not detected when finding deps for " lib " in coordinate " (pr-str coord))
             {:lib lib :coord coord}))))

(defmulti coord-deps
  "Return coll of immediate [lib coord] external deps for this library."
  (fn [lib coord manifest-type config] manifest-type))

(defmethod coord-deps :default [lib coord manifest-type config]
  (throw-bad-manifest lib coord manifest-type))

(defmulti coord-paths
  "Return coll of classpath roots for this library on disk."
  (fn [lib coord manifest-type config] manifest-type))

(defmethod coord-paths :default [lib coord manifest-type config]
  (throw-bad-manifest lib coord manifest-type))

