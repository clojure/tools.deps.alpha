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
(s/def ::providers (s/keys :opt-un [::mvn ::git]))

;; maven provider
(s/def ::url string?)
(s/def ::mvn (s/keys :opt [::repos]))
(s/def ::repo (s/keys :opt-un [::url]))
(s/def ::repo-id string?)
(s/def ::repos (s/map-of ::repo-id ::repo))

;; git provider - TODO
(s/def ::git (s/keys))

;; resolve-deps args
(s/def ::resolve-args (s/keys :opt-un [::extra-deps ::override-deps ::default-deps]))
(s/def ::extra-deps (s/map-of ::lib ::coord))
(s/def ::override-deps (s/map-of ::lib ::coord))
(s/def ::default-deps (s/map-of ::lib ::coord))

;; make-classpath overrides
(s/def ::classpath-overrides (s/map-of ::lib ::path))

;; deps map (format of the deps.edn file)
(s/def ::alias simple-keyword?)
(s/def ::aliases (s/map-of ::alias (s/or :resolve-deps ::resolve-args :make-classpath ::classpath-overrides)))
(s/def ::deps (s/map-of ::lib ::coord))
(s/def ::deps-map (s/keys :opt-un [::deps ::resolve-args ::providers ::aliases]))

;; lib map
(s/def ::dependents (s/coll-of ::lib))
(s/def ::resolved-coord (s/merge ::coord (s/keys :req-un [::path] :opt-un [::dependents])))
(s/def ::lib-map (s/map-of ::lib ::resolved-coord))

;; classpath
(s/def ::classpath string?)
