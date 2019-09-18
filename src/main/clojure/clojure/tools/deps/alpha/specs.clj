;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.specs
  (:require [clojure.spec.alpha :as s]))

;; library, like org.clojure/clojure
(s/def ::lib symbol?)

;; coordinates

(s/def ::path string?)

(s/def :mvn/version string?)
(s/def ::exclusions (s/coll-of ::lib))
(s/def :mvn/coord (s/keys :req [:mvn/version] :opt-un [::path ::exclusions]))

(s/def :local/root string?)
(s/def :local/coord (s/keys :req [:local/root] :opt-un [::path]))

(s/def :git/url string?)
(s/def :git/sha string?)
(s/def :git/coord (s/keys :req [:git/url] :req-un [:git/sha]))

;; should this become a multipec?
(s/def ::coord (s/or :mvn :mvn/coord
                     :local :local/coord
                     :git :git/coord))

;; resolve-deps args
;;   used to modify the expanded deps tree
;;   ::extra-deps - map of lib to coordinate added to the initial deps collection
;;   ::override-deps - map of lib to coordinate to use instead of the coord found during expansion
;;   ::default-deps - map of lib to coordinate to use if no coord is specified in extension
(s/def ::resolve-args (s/keys :opt-un [::extra-deps ::override-deps ::default-deps]))
(s/def ::extra-deps (s/map-of ::lib ::coord))
(s/def ::override-deps (s/map-of ::lib ::coord))
(s/def ::default-deps (s/map-of ::lib ::coord))

;; make-classpath args
;;   used when constructing the classpath
;;   ::classpath-overrides - map of lib to path to use instead of the artifact found during resolution
;;   ::extra-paths - collection of extra paths to add to the classpath in addition to ::paths
(s/def ::classpath-args (s/keys :opt-un [::classpath-overrides ::extra-paths]))
(s/def ::classpath-overrides (s/map-of ::lib ::path))
(s/def ::extra-paths (s/coll-of string? :kind vector? :into []))

;; deps map (format of the deps.edn file)
(s/def ::paths (s/coll-of string? :kind vector? :into []))
(s/def ::deps (s/map-of ::lib ::coord))
(s/def ::alias keyword?)
(s/def ::aliases (s/map-of ::alias (s/or :resolve-deps ::resolve-args :make-classpath ::classpath-args)))
(s/def ::deps-map (s/keys :opt-un [::paths ::deps ::aliases]))

;; lib map
;;   a map of lib to resolved coordinate (a coord with a ::path) and dependent info
(s/def ::dependents (s/coll-of ::lib))
(s/def ::resolved-coord (s/merge ::coord (s/keys :req-un [::paths] :opt-un [::dependents])))
(s/def ::lib-map (s/map-of ::lib ::resolved-coord))

;; classpath
(s/def ::classpath string?)

(s/def ::run (s/keys :req-un [::deps-map ::lib-map ::classpath]))

;; Procurers

;; maven 
(s/def :mvn/repos (s/map-of ::repo-id ::repo))
(s/def ::repo-id string?)
(s/def ::repo (s/keys :opt-un [::url]))
(s/def ::url string?)
(s/def :mvn/local-repo string?)

;; API

(s/fdef clojure.tools.deps.alpha/resolve-deps
  :args (s/cat :deps ::deps-map :options ::resolve-args)
  :ret ::lib-map)

(s/fdef clojure.tools.deps.alpha/make-classpath
  :args (s/cat :libs ::lib-map :paths ::paths :classpath-args ::classpath-args)
  :ret string?)

(comment
  ;; some scratch code to recursively check every deps.edn under
  ;; a root directory whether it's valid against the specs
  (require
    '[clojure.spec.test.alpha :as stest]
    '[clojure.tools.deps.alpha.reader :as trdr]
    '[clojure.java.io :as jio])
  (import '[java.nio.file Files Paths FileVisitor FileVisitResult])
  (stest/instrument (stest/enumerate-namespace 'clojure.tools.deps.alpha))

  (Files/walkFileTree
    (Paths/get "../" (into-array String []))
    (reify FileVisitor
      (postVisitDirectory [_ dir ex] FileVisitResult/CONTINUE)
      (preVisitDirectory [_ dir attrs] FileVisitResult/CONTINUE)
      (visitFileFailed [_ f ex] FileVisitResult/CONTINUE)
      (visitFile [_ f attrs]
        (when (.endsWith (str f) "/deps.edn")
          (print "Checking" (str f))
          (let [v (s/valid? ::deps-map (#'trdr/slurp-edn-map (.toFile f)))]
            (println ":" v)
            (when-not v
              (s/explain ::deps-map (#'trdr/slurp-edn-map (.toFile f))))))
        FileVisitResult/CONTINUE)))
  )
