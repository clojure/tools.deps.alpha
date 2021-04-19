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
    [clojure.string :as str]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.gitlibs :as gitlibs]))

(def ^:private git-services
  {:github {:service #"^(?:com|io).github.([^.]+)$" :url "https://github.com/%s/%s.git"}
   :gitlab {:service #"^(?:com|io).gitlab.([^.]+)$" :url "https://gitlab.com/%s/%s.git"}
   :bitbucket {:service #"^(?:org|io).bitbucket.([^.]+)$" :url "https://bitbucket.org/%s/%s.git"}
   :beanstalk {:service #"^(?:com|io).beanstalkapp.([^.]+)$" :url "https://%s.git.beanstalkapp.com/%s.git"}})

(defn auto-git-url
  "Create url from lib name, ie:
    io.github.foo/bar => https://github.com/foo/bar.git
   or return nil if not a name that can be auto converted to a url."
  [lib]
  (let [group (namespace lib)
        project (name lib)]
    (some (fn [{:keys [service url]}]
            (when-let [matches (re-matches service group)]
              (format url (second matches) project)))
      (vals git-services))))

(defn full-sha?
  [sha]
  (boolean (and sha (= 40 (count sha)))))

(defmethod ext/coord-type-keys :git
  [_type]
  #{:git/url :git/sha :git/tag :sha})

(defn- coord-err
  ^Throwable [msg lib coord]
  (ex-info msg {:lib lib :coord coord}))

(defn- make-standard
  [coord url sha tag]
  (->
    (cond-> coord
      url (assoc :git/url url)
      sha (assoc :git/sha sha)
      tag (assoc :git/tag tag))
    (dissoc :sha :tag)))

(defmethod ext/canonicalize :git
  [lib {unsha :sha untag :tag :git/keys [url sha tag] :as coord} _config]
  (when (nil? (namespace lib)) (throw (coord-err (format "Invalid lib name: %s" lib) lib coord)))
  (when (and unsha sha) (throw (coord-err (format "git coord has both :sha and :git/sha for %s" lib) lib coord)))
  (when (and untag tag) (throw (coord-err (format "git coord has both :tag and :git/tag for %s" tag) lib coord)))

  (let [canon-sha (or sha unsha)
        canon-tag (or tag untag)
        canon-url (or url (auto-git-url lib))]
    (when (and canon-tag (not (some #{canon-tag} (gitlibs/tags canon-url))))
      (throw (coord-err (format "Library %s has invalid tag: %s" lib canon-tag) lib coord)))
    (if canon-sha
      (if canon-tag
        (let [full-sha (if (full-sha? canon-sha) canon-sha (gitlibs/resolve canon-url canon-sha))]
          (when-not (= (gitlibs/resolve canon-url canon-sha) (gitlibs/resolve canon-url canon-tag))
            (throw (coord-err (format "Library %s has sha and tag that point to different commits" lib) lib coord)))
          [lib (make-standard coord canon-url full-sha canon-tag)])
        (if (full-sha? canon-sha)
          [lib (make-standard coord canon-url canon-sha canon-tag)]
          (throw (ex-info (format "Library %s has prefix sha, use full sha or add tag" lib) {:lib lib :coord coord}))))
      (throw (ex-info (format "Library %s has coord with missing sha" lib) {:lib lib :coord coord})))))

(defmethod ext/lib-location :git
  [lib {unsha :sha sha :git/sha} _]
  {:base (str (gitlibs/cache-dir) "/libs") ;; gitlibs repo location is not in a public API...
   :path (str lib "/" (or sha unsha))
   :type :git})

(defmethod ext/dep-id :git
  [_lib {url :git/url, unsha :sha, sha :git/sha} _config]
  {:git/url url, :git/sha (or sha unsha)})

(defmethod ext/manifest-type :git
  [lib {unsha :sha :git/keys [url sha] :deps/keys [manifest root] :as _coord} _config]
  (let [sha (or sha unsha)
        sha-dir (gitlibs/procure url lib sha)
        root-dir (if root
                   (let [root-file (jio/file root)]
                     (if (.isAbsolute root-file) ;; should be only after coordinate resolution
                       (.getCanonicalPath root-file)
                       (.getCanonicalPath (jio/file sha-dir root-file))))
                   sha-dir)]
    (if manifest
      {:deps/manifest manifest, :deps/root root-dir}
      (ext/detect-manifest root-dir))))

(defmethod ext/coord-summary :git [lib {unsha :sha :git/keys [url sha]}]
  (str lib " " url " " (subs (or sha unsha) 0 7)))

;; 0 if x and y are the same commit
;; negative if x is parent of y (y derives from x)
;; positive if y is parent of x (x derives from y)
(defmethod ext/compare-versions [:git :git]
  [lib {x-url :git/url, x-unsha :sha, x-sha :git/sha :as x} {y-url :git/url, y-unsha :sha, y-sha :git/sha :as y} _config]
  (let [x-sha (or x-sha x-unsha)
        y-sha (or y-sha y-unsha)]
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
          (nil? desc) (throw (ex-info (str "No known ancestor relationship between git versions for " lib "\n"
                                        "  " x-url " at " x-sha "\n"
                                        "  " y-url " at " y-sha)
                               {:x x :y y}))
          (= desc x-sha) 1
          (= desc y-sha) -1)))))

(defmethod ext/manifest-type :git
  [lib {unsha :sha :git/keys [url sha] :deps/keys [manifest root] :as _coord} _config]
  (let [sha (or sha unsha)
        sha-dir (gitlibs/procure url lib sha)
        root-dir (if root
                   (let [root-file (jio/file root)]
                     (if (.isAbsolute root-file) ;; should be only after coordinate resolution
                       (.getCanonicalPath root-file)
                       (.getCanonicalPath (jio/file sha-dir root-file))))
                   sha-dir)]
    (if manifest
      {:deps/manifest manifest, :deps/root root-dir}
      (ext/detect-manifest root-dir))))

(defmethod ext/find-versions :git
  [lib {:keys [git/url] :as coord} _coord-type _config]
  (let [url (or url (auto-git-url lib))]
    (try
      (map (fn [tag] {:git/tag tag}) (gitlibs/tags url))
      (catch Throwable _ nil))))

(comment
  (ext/find-versions 'org.clojure/spec.alpha
    {:git/url "https://github.com/clojure/spec.alpha.git"} :git nil)

  (ext/lib-location 'foo/foo
                    {:git/url "https://github.com/clojure/core.async.git"
                     :sha "ecea2539a724a415b15e50f12815b4ab115cfd35"} {})

  (binding [*print-namespace-maps* false]
    (run! prn
      (ext/find-versions 'io.github.clojure/tools.deps.alpha nil :git nil)))

  ;; error - prefix sha
  (ext/canonicalize 'org.clojure/spec.alpha
    {:git/url "https://github.com/clojure/spec.alpha.git" :sha "739c1af5"}
    nil)

  (ext/dep-id 'org.clojure/spec.alpha
    {:git/url "https://github.com/clojure/spec.alpha.git" :sha "739c1af56dae621aedf1bb282025a0d676eff713"}
    nil)

  (ext/manifest-type 'org.clojure/spec.alpha
    {:git/url "https://github.com/clojure/spec.alpha.git" :git/sha "739c1af56dae621aedf1bb282025a0d676eff713"}
    nil)

  (ext/compare-versions
    'org.clojure/spec.alpha
    {:git/url "https://github.com/clojure/spec.alpha.git" :git/sha "739c1af56dae621aedf1bb282025a0d676eff713"}
    {:git/url "git@github.com:clojure/spec.alpha.git" :git/sha "a65fb3aceec67d1096105cab707e6ad7e5f063af"}
    nil)

  )
