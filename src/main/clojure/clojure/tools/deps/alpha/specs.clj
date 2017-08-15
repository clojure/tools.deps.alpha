(ns clojure.tools.deps.alpha.specs
  (:require [clojure.spec.alpha :as s]))

;; library, like org.clojure/clojure
(s/def ::lib symbol?)

;; coordinate, like {:type :mvn :version "0.1.2"}
(defmulti coord :type)
(s/def ::coord (s/multi-spec coord :type))
(s/def ::type keyword?)
(s/def ::path string?)
(s/def ::version string?)

(defmethod coord :mvn [_] (s/keys :opt-un [::version ::path]))
(defmethod coord :file [_] (s/keys :req-un [::path]))

;; providers
(s/def ::providers (s/keys :opt-un [::mvn]))

;; maven provider
(s/def ::url string?)
(s/def ::mvn (s/keys :opt [::repos]))
(s/def ::repo (s/keys :opt-un [::url]))
(s/def ::repo-id string?)
(s/def ::repos (s/map-of ::repo-id ::repo))

;; resolve-deps args
;;   used to modify the expanded deps tree
;;   ::extra-deps - map of lib to coordinate added to the initial deps collection
;;   ::override-deps - map of lib to coordinate to use instead of the coord found during expansion
;;   ::default-deps - map of lib to coordinate to use if no coord is specified in extension
(s/def ::resolve-args (s/keys :opt-un [::extra-deps ::override-deps ::default-deps]))
(s/def ::extra-deps (s/map-of ::lib ::coord))
(s/def ::override-deps (s/map-of ::lib ::coord))
(s/def ::default-deps (s/map-of ::lib ::coord))

;; make-classpath overrides
;;   specify a map of lib to path to use instead of the artifact found during resolution
(s/def ::classpath-overrides (s/map-of ::lib ::path))

;; deps map (format of the deps.edn file)
;;   ::deps - a map of library to coordinate (which has a provider type)
;;   ::providers - a map of artifact provider type to provider configuration
;;   ::resolve-args - a map with the same structure as the resolve-deps second arg
;;   ::aliases - a map from keyword (the alias) to a resolve-args map OR a make-classpath overrides map
(s/def ::alias simple-keyword?)
(s/def ::aliases (s/map-of ::alias (s/or :resolve-deps ::resolve-args :make-classpath ::classpath-overrides)))
(s/def ::deps (s/map-of ::lib ::coord))
(s/def ::deps-map (s/keys :opt-un [::deps ::resolve-args ::providers ::aliases]))

;; lib map
;;   a map of lib to resolved coordinate (a coord with a ::path) and dependent info
(s/def ::dependents (s/coll-of ::lib))
(s/def ::resolved-coord (s/merge ::coord (s/keys :req-un [::path] :opt-un [::dependents])))
(s/def ::lib-map (s/map-of ::lib ::resolved-coord))

;; classpath
(s/def ::classpath string?)

;; API

(s/fdef clojure.tools.deps.alpha/resolve-deps
  :args (s/cat :deps ::deps-map :options ::resolve-args)
  :ret ::lib-map)

(s/fdef clojure.tools.deps.alpha/make-classpath
  :args (s/cat :libs ::lib-map :overrides ::classpath-overrides)
  :ret ::classpath)