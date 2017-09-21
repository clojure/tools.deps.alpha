;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.providers)

(defn coord-type
  "The namespace (as a keyword) of the only qualified key in the coordinate,
   excluding the reserved deps namespace."
  [coord]
  (->> coord keys (keep namespace) (remove #(= "deps" %)) first keyword))

(defmulti expand-dep
  "Takes a lib, a coordinate, and the root config. Dispatch based on the coordinate's
  provider type to a provider to expand that coordinate into its immediate
  dependencies. The return is a collection of [lib coord] elements."
  (fn [lib coord config] (coord-type coord)))

(defmulti download-dep
  "Given a lib, a coordinate, and the root config. Dispatch to the provider
  to download/obtain the dependency. Return the coord with an added :path key that
  points to the downloaded artifact."
  (fn [lib coord config] (coord-type coord)))

(defmulti compare-versions
  "Given two coordinates, use this as a comparator returning a negative number, zero,
  or positive number when coord-x is logically 'less than', 'equal to', or 'greater than'
  coord-y. The dispatch occurs on the type of x and y."
  (fn [coord-x coord-y] [(coord-type coord-x) (coord-type coord-y)]))