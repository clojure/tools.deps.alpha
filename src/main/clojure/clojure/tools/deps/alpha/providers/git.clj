;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.providers.git
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.providers :as providers]
    [clojure.tools.deps.alpha.util.io :refer [printerrln]])
  (:import
    [java.io File]
    [org.eclipse.jgit.api Git]
    [org.eclipse.jgit.lib RepositoryBuilder]))

(set! *warn-on-reflection* true)

;;;; Git

(defn- clean-url
  [url]
  (last (str/split url #"://")))

(defn- git-repo
  ^Git [^File git-dir ^File work-tree]
  (Git. (.. (RepositoryBuilder.) (setGitDir git-dir) (setWorkTree work-tree) build)))

(defn- git-fetch
  ^Git [^File git-dir ^File work-tree]
  (let [git (git-repo git-dir work-tree)]
    (.. git fetch call)
    git))

;; TODO: restrict clone to an optional refspec?
(defn- git-clone-bare
  ^Git [^String url ^File git-dir ^File rev-dir]
  (.. (Git/cloneRepository) (setURI url) (setGitDir git-dir)
    (setBare true)
    (setNoCheckout true)
    (setCloneAllBranches true)
    call)
  (git-repo git-dir rev-dir))

(defn- git-checkout
  ^File [^Git git ^String rev]
  (.. git checkout (setStartPoint rev) (setAllPaths true) call)
  (.. git getRepository getWorkTree))

;;;; Provider methods

(defmethod providers/dep-id :git
  [lib {:keys [git/url rev] :as coord}]
  {:url url, :rev rev})

(defn- ensure-cache
  "Download git for the specified url and return the cached rev dir"
  ^File [^File cache-root url rev]
  (let [rev-dir (jio/file cache-root "git" "revs" rev)]
    (if (.exists rev-dir)
      rev-dir
      (let [git-dir (jio/file cache-root "git" "repos" (clean-url url))]
        (->
          (if (.exists git-dir)
            (git-fetch git-dir rev-dir)
            (git-clone-bare url git-dir rev-dir))
          (git-checkout rev))))))

(defmethod providers/manifest-type :git
  [lib {:keys [git/url rev deps/manifest] :as coord} {:keys [deps/cache-dir]}]
  (let [dir (jio/file cache-dir)
        tree (ensure-cache dir url rev)]
    (if manifest
      {:deps/manifest manifest, :deps/root tree}
      (providers/detect-manifest tree))))

(defmethod providers/compare-versions [:git :git]
  [coord-x coord-y]
  ;; TODO
  (throw (ex-info "Unresolvable version conflict with two git coordinates for the same library" {:x coord-x :y coord-y})))

(comment
  (#'ensure-cache
    (File. "/Users/alex/code/.clojure/.cpcache")
    "https://github.com/clojure/spec.alpha.git"
    "739c1af56dae621aedf1bb282025a0d676eff713")
  )