;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.specs
  (:require [clojure.spec.alpha :as s]))

;;;; generic types

(s/def ::lib symbol?)
(s/def ::path string?)
(s/def ::alias keyword?)

;;;; coordinates

;; generic coord attributes
(s/def :deps/root ::path)
(s/def :deps/manifest keyword?)
(s/def :deps/exclusions (s/coll-of ::lib))
(s/def :deps/coord (s/keys :opt [:deps/root :deps/manifest] :opt-un [:deps/exclusions]))

;; maven coords
(s/def :mvn/version string?)
(s/def :mvn/coord (s/merge :deps/coord
                    (s/keys :req [:mvn/version])))

;; local coords
(s/def :local/root string?)
(s/def :local/coord (s/merge :deps/coord
                      (s/keys :req [:local/root])))

;; git coords
(s/def :git/url string?)
(s/def :git/sha string?)
(s/def :git/tag string?)
(s/def :git/coord (s/merge :deps/coord
                    (s/keys :opt [:git/url :git/sha :git/tag])))

;; should this become a multispec?
(s/def ::coord (s/nilable
                 (s/or :mvn :mvn/coord
                       :local :local/coord
                       :git :git/coord)))

(s/def ::path-ref (s/or :path ::path :alias ::alias))
(s/def :aliased/paths (s/coll-of ::path-ref :kind vector? :into []))

;; tool args
;;   ::replace-deps - map of lib to coordinate to replace the
(s/def ::tool-args (s/keys :opt-un [::replace-deps ::replace-paths ::deps ::paths]))
(s/def ::replace-deps (s/map-of ::lib ::coord))
(s/def ::replace-paths :aliased/paths)

;; resolve-deps args - used to modify the expanded deps tree
;;   ::extra-deps - map of lib to coordinate added to the initial deps collection
;;   ::override-deps - map of lib to coordinate to use instead of the coord found during expansion
;;   ::default-deps - map of lib to coordinate to use if no coord is specified in extension
(s/def ::resolve-args (s/keys :opt-un [::extra-deps ::override-deps ::default-deps]))
(s/def ::extra-deps (s/map-of ::lib ::coord))
(s/def ::override-deps (s/map-of ::lib ::coord))
(s/def ::default-deps (s/map-of ::lib ::coord))
(s/def ::threads pos-int?)
(s/def ::trace boolean?)

;; make-classpath args - used when constructing the classpath
;;   ::classpath-overrides - map of lib to path to use instead of the artifact found during resolution
;;   ::extra-paths - collection of extra paths to add to the classpath in addition to ::paths
(s/def ::classpath-args (s/keys :opt-un [::classpath-overrides ::extra-paths]))
(s/def ::classpath-overrides (s/map-of ::lib ::path))
(s/def ::extra-paths :aliased/paths)

;; exec args - used when executing a function with -X or -T
;;   ::exec-args - map of default function args
;;   ::exec-fn - default function symbol
;;   ::ns-default - default namespace to use when resolving functions
;;   ::ns-aliases - map of alias to namespace to use when resolving functions
(s/def ::exec-args (s/keys :opt-un [::exec-args ::exec-fn ::ns-default ::ns-aliases]))
(s/def ::exec-args (s/nilable map?))
(s/def ::ns-default simple-symbol?)
(s/def ::ns-aliases (s/map-of simple-symbol? simple-symbol?))

;; deps map (format of the deps.edn file)
(s/def ::paths :aliased/paths)
(s/def ::deps (s/map-of ::lib ::coord))
(s/def ::aliases (s/map-of ::alias any?))
(s/def ::deps-map (s/keys
                    :opt-un [::paths ::deps ::aliases]
                    :opt [:mvn/repos :mvn/local-repo :tools/usage :deps/prep-lib]))

;; lib map
;;   a map of lib to resolved coordinate (a coord with a ::path) and dependent info
(s/def ::dependents (s/coll-of ::lib))
(s/def ::resolved-coord (s/merge ::coord (s/keys :opt-un [:aliased/paths ::dependents])))
(s/def ::lib-map (s/map-of ::lib ::resolved-coord))

;; classpath
(s/def ::classpath string?)

;; Procurers

;; Maven
(s/def :mvn/repos (s/map-of ::repo-id ::repo))
(s/def ::repo-id string?)
(s/def ::repo (s/nilable (s/keys :opt-un [::url])))
(s/def ::url string?)
(s/def :mvn/local-repo string?)

;; Tool usage
(s/def :tools/usage (s/keys :opt-un [::ns-default ::ns-aliases]))

;; Prep lib
(s/def :deps/prep-lib (s/keys :req-un [:prep/ensure ::alias :prep/fn]))
(s/def :prep/ensure ::path)
(s/def :prep/fn symbol?)

;; API

(s/fdef clojure.tools.deps.alpha/resolve-deps
  :args (s/cat :deps ::deps-map :options ::resolve-args)
  :ret ::lib-map)

(s/fdef clojure.tools.deps.alpha/make-classpath-map
  :args (s/cat :deps ::deps-map, :libs ::lib-map, :classpath-args ::claspath-args)
  :ret map?)

(s/fdef clojure.tools.deps.alpha/make-classpath
  :args (s/cat :libs ::lib-map, :paths ::paths, :classpath-args ::classpath-args)
  :ret string?)

(comment
  ;; some scratch code to recursively check every deps.edn under
  ;; a root directory whether it's valid against the specs
  (require
    '[clojure.spec.test.alpha :as stest]
    '[clojure.tools.deps.alpha :as deps])
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
          (let [v (s/valid? ::deps-map (#'deps/slurp-edn-map (.toFile f)))]
            (println ":" v)
            (when-not v
              (s/explain ::deps-map (#'deps/slurp-edn-map (.toFile f))))))
        FileVisitResult/CONTINUE)))
  )
