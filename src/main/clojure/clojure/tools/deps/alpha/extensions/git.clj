;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.alpha.extensions.git
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.gitlibs :as gitlibs]))

(defmethod ext/canonicalize :git
  [lib {:keys [sha tag rev] :as coord} _config]
  (let [lib (if (nil? (namespace lib))
              (symbol (name lib) (name lib))
              lib)]
    (cond
      (and sha (= 40 (count sha))) [lib coord]
      sha (throw (ex-info (str "Prefix sha not supported, use full sha for " lib) {:lib lib :coord coord}))
      tag (throw (ex-info (str "Library " lib " has :tag but no :sha.\nAdd :sha or run `clj -Sresolve-tags` to update deps.edn.")
                   {:lib lib :coord coord}))
      rev (throw (ex-info (str "Library " lib " has deprecated :rev attribute - use :sha or :tag instead.")
                   {:lib lib :coord coord}))
      :else (throw (ex-info (str "Library " lib " has missing :sha in coordinate.") {:lib lib :coord coord})))))

(defmethod ext/lib-location :git
  [lib {:keys [sha]} _]
  {:base (str (gitlibs/cache-dir) "/libs") ;; gitlibs repo location is not in a public API...
   :path (str lib "/" sha)
   :type :git})

(defmethod ext/dep-id :git
  [_lib coord _config]
  (select-keys coord [:git/url :sha]))

(defmethod ext/manifest-type :git
  [lib {:keys [git/url sha deps/manifest deps/root] :as _coord} _config]
  (let [sha-dir (gitlibs/procure url lib sha)
        root-dir (if root
                   (let [root-file (jio/file root)]
                     (if (.isAbsolute root-file) ;; should be only after coordinate resolution
                       (.getCanonicalPath root-file)
                       (.getCanonicalPath (jio/file sha-dir root-file))))
                   sha-dir)]
    (if manifest
      {:deps/manifest manifest, :deps/root root-dir}
      (ext/detect-manifest root-dir))))

(defmethod ext/coord-summary :git [lib {:keys [git/url sha]}]
  (str lib " " url " " (subs sha 0 7)))

;; 0 if x and y are the same commit
;; negative if x is parent of y (y derives from x)
;; positive if y is parent of x (x derives from y)
(defmethod ext/compare-versions [:git :git]
  [_lib {x-url :git/url, x-sha :sha :as x} {y-url :git/url, y-sha :sha :as y} _config]
  (if (= x-sha y-sha)
    0
    (let [desc (if (= x-url y-url)
                 (or
                   (gitlibs/descendant x-url [x-sha y-sha])
                   (gitlibs/descendant y-url [x-sha y-sha]))
                 (and
                   (gitlibs/descendant x-url [x-sha y-sha])
                   (gitlibs/descendant y-url [x-sha y-sha])))]
      (cond
        (nil? desc) (throw (ex-info "No known relationship between git versions" {:x x :y y}))
        (= desc x-sha) 1
        (= desc y-sha) -1))))

(comment
  (ext/lib-location 'foo/foo
                    {:git/url "https://github.com/clojure/core.async.git"
                     :sha "ecea2539a724a415b15e50f12815b4ab115cfd35"} {})

  ;; error - prefix sha
  (ext/canonicalize 'org.clojure/spec.alpha
    {:git/url "https://github.com/clojure/spec.alpha.git" :sha "739c1af5"}
    nil)

  (ext/dep-id 'org.clojure/spec.alpha
    {:git/url "https://github.com/clojure/spec.alpha.git" :sha "739c1af56dae621aedf1bb282025a0d676eff713"}
    nil)

  (ext/manifest-type 'org.clojure/spec.alpha
    {:git/url "https://github.com/clojure/spec.alpha.git" :sha "739c1af56dae621aedf1bb282025a0d676eff713"}
    nil)

  (ext/compare-versions
    'org.clojure/spec.alpha
    {:git/url "https://github.com/clojure/spec.alpha.git" :sha "739c1af56dae621aedf1bb282025a0d676eff713"}
    {:git/url "git@github.com:clojure/spec.alpha.git" :sha "a65fb3aceec67d1096105cab707e6ad7e5f063af"}
    nil)
  )
