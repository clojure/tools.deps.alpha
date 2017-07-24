(ns clojure.tools.deps.alpha.providers)

(defmulti expand-dep
  "Takes a lib, a coordinate, and a provider and dispatch based on the coordinate's
  provider type to a provider to expand that coordinate into its immediate
  dependencies. The return is a collection of [lib coord] elements."
  (fn [lib coord provider] (:type coord)))

(defmulti download-dep
  "Given a lib, a coordinate, and the coordinate's provider, dispatch to the provider
  to download/obtain the dependency. Return the coord with an added :path key that
  points to the downloaded artifact."
  (fn [lib coord provider] (:type coord)))

