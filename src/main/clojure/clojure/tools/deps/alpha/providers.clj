;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.providers)

;; Methods switching on coordinate type

(defn coord-type
  "The namespace (as a keyword) of the only qualified key in the coordinate,
   excluding the reserved deps namespace."
  [coord]
  (->> coord keys (keep namespace) (remove #(= "deps" %)) first keyword))

(defmulti dep-id
  "Returns an identifier value that can be used to detect a lib/coord cycle while
   expanding deps."
  (fn [lib coord] (coord-type coord)))

(defmulti manifest-type
  "Takes a lib, a coord, and the . Dispatch based on the
  coordinate type. Determine and return the manifest type
  for this coordinate."
  (fn [lib coord config] (coord-type coord)))

;; Version comparison, either within or across coordinate types

(defmulti compare-versions
  "Given two coordinates, use this as a comparator returning a negative number, zero,
  or positive number when coord-x is logically 'less than', 'equal to', or 'greater than'
  coord-y. The dispatch occurs on the type of x and y."
  (fn [coord-x coord-y] [(coord-type coord-x) (coord-type coord-y)]))

;; Methods switching on manifest provider type

(defmulti coord-deps
  "Return coll of immediate [lib coord] external deps for this library."
  (fn [lib coord manifest-type config] manifest-type))

(defmulti coord-paths
  "Return coll of classpath roots for this library on disk."
  (fn [lib coord manifest-type config] manifest-type))