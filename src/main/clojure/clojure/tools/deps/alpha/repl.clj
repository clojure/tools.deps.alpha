;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.repl
  (:require
    [clojure.java.io :as jio]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.deps.alpha :as deps]
    [clojure.data.json :as json]
    [clj-http.client :as http]

    ;; find-revs
    [clojure.tools.deps.alpha.util.maven :as maven]
    [clojure.tools.deps.alpha.util.session :as session])
  (:import
    [clojure.lang DynamicClassLoader]
    [java.net URLEncoder]
    [java.io File]

    ;; find-revs
    [org.eclipse.aether RepositorySystem RepositorySystemSession]
    [org.eclipse.aether.resolution VersionRangeRequest]))

(set! *warn-on-reflection* true)

;; maintain basis

(defn- read-basis
  []
  (when-let [f (jio/file (System/getProperty "clojure.basis"))]
    (if (and f (.exists f))
      (deps/slurp-deps f)
      (throw (IllegalArgumentException. "No basis declared in clojure.basis system property")))))

(defonce ^:private init-basis (delay (read-basis)))

(defn launch-basis
  "Initial runtime basis at launch"
  []
  @init-basis)

(def ^:private runtime-basis
  (atom nil))

(defn- reset-basis
  [basis]
  (reset! runtime-basis basis))

(defn current-basis
  "Return the current runtime basis, which may have been modified since the launch."
  []
  (or @runtime-basis (reset-basis @init-basis)))

;; add-libs

(defn- add-loader-url
  "Add url string or URL to the highest level DynamicClassLoader url set."
  [url]
  (let [u (if (string? url) (java.net.URL. url) url)
        loader (loop [loader (.getContextClassLoader (Thread/currentThread))]
                 (let [parent (.getParent loader)]
                   (if (instance? DynamicClassLoader parent)
                     (recur parent)
                     loader)))]
    (if (instance? DynamicClassLoader loader)
      (.addURL ^DynamicClassLoader loader u)
      (throw (IllegalAccessError. "Context classloader is not a DynamicClassLoader")))))

(defn add-libs
  "Add map of lib to coords to the current runtime environment. All transitive
  dependencies will also be considered (in the context of the current set
  of loaded dependencies) and new transitive dependencies will also be
  loaded. Returns seq of all added libs or nil if couldn't be loaded.

  Note that for successful use, you must be in a REPL environment where a
  valid parent DynamicClassLoader can be found in which to add the new lib
  urls.

  Example:
   (add-libs '{org.clojure/core.memoize {:mvn/version \"0.7.1\"}})"
  [lib-coords]
  (let [{:keys [libs] :as initial-basis} (current-basis)]
    (if (empty? (set/difference (-> libs keys set) (-> lib-coords keys set)))
      nil ;; already loaded
      (let [updated-deps (reduce-kv (fn [m k v] (assoc m k (dissoc v :dependents :paths))) lib-coords libs)
            updated-edn (merge (dissoc initial-basis :libs :classpath :deps) {:deps updated-deps})
            {updated-libs :libs :as updated-basis} (deps/calc-basis updated-edn)
            new-libs (select-keys updated-libs (set/difference (set (keys updated-libs)) (set (keys libs))))
            paths (mapcat :paths (vals new-libs))
            urls (->> paths (map jio/file) (map #(.toURL ^File %)))]
        ;; TODO: multiple unsynchronized changes to runtime state - coordinate with lock?
        (run! add-loader-url urls)
        (reset-basis updated-basis)
        (keys new-libs)))))

;; Finding libs and versions

(defn libs
  "List all libraries in the basis"
  []
  (run! println (sort (keys (get (current-basis) :libs)))))

(defn- central-search
  [search tagged? count]
  (try
    (let [encoded (URLEncoder/encode (if tagged? (str search " tags:clojure") search))
          url (format "https://search.maven.org/solrsearch/select?q=%s&rows=%d&wt=json" encoded count)
          json-resp (slurp url)
          resp (json/read-str json-resp)
          results (get-in resp ["response" "docs"])]
      (map (fn [{:strs [g a latestVersion timestamp versionCount]}]
             (let [lib (symbol g a)]
               {:dep {lib {:mvn/version latestVersion}}
                :score (if tagged?
                         (cond
                           (or (= search (str lib)) (= search a)) 5
                           (str/includes? a search) 4
                           (str/includes? g search) 3
                           :else 2)
                         (if (= a search) 3 1))
                :versions versionCount
                :updated timestamp
                :from :mvn-central}))
        results))
    (catch Throwable _e)))

(defn- maven-central
  [search max-count]
  (let [central-clj-results (central-search search true max-count)
        central-results (when (< (count central-clj-results) max-count) (central-search search false max-count))]
    (take max-count (concat central-clj-results central-results))))

(defn- clojars
  [search max-count]
  (let [encoded (URLEncoder/encode search)
        url (format "https://clojars.org/search?q=%s&format=json" search)
        json-resp (slurp url)
        resp (json/read-str json-resp)
        results (get resp "results")]
    (->> results
      (map (fn [{:strs [group_name jar_name version description created] :as r}]
             (let [lib (symbol group_name jar_name)]
               {:dep {lib {:mvn/version version}}
                :score (cond
                         (= search group_name jar_name) 5
                         (= search jar_name search) 5
                         (= search (str lib)) 5
                         (= r (first results)) 4
                         :else 3)
                :created created
                :description description
                :from :clojars})))
      (take max-count))))

;; https://github.com/search?l=Clojure&q=cheshire&type=Repositories
(defn- github
  [search max-count]
  (let [encoded (URLEncoder/encode search)
        url (format "https://grep.app/api/search?q=%s" encoded)
        json-resp (:body (http/get url {:accept :json}))
        resp (json/read-str json-resp)
        results (get-in resp ["facets" "repo" "buckets"])]
    (->> results
      (map (fn [{:strs [val]}]
             (let [a (name (symbol val))
                   lib (symbol (str "github-" val))]
               {:dep {lib {:git/url (format "https://github.com/%s.git" val) :sha "TBD"}}
                :score (cond
                         (or (= search val) (= search a)) 5
                         (str/includes? a search) 3
                         :else 1)
                :from :github})))
      (sort-by :score)
      reverse
      (take max-count))))

(defn find-lib*
  "Find libraries based on a search string in a variety of sources, like Maven Central, Clojars, or GitHub."
  [search {:keys [sources max-count] :or {max-count 10}}]
  (let [central (maven-central search max-count)
        clojars (clojars search max-count)
        gh (github search max-count)]
    (->> (concat central clojars gh)
      (sort-by :score)
      reverse
      (take max-count))))

(defn find-lib
  [search & opts]
  (binding [*print-namespace-maps* false]
    (let [results (find-lib* search (apply hash-map opts))]
      (run! #(prn (:dep %) (:score %) (:from %)) results))))

(comment
  (maven-central "s3" 10)
  (central-search "s3" true 10)
  (clojars "json" 10)
  (github "tools.deps.alpha" 10)
  (github "cognitect/test-runner" 20)

  (find-lib "http")
  (find-lib "clj-http")
  (find-lib "async" :max-count 15)
  (find-lib "core.async")
  (find-lib "org.clojure/core.async" :max-count 20)
  (find-lib "transit" :max-count 20)
  (find-lib "transit-clj")
  (find-lib "cognitect")
  (find-lib "s3")
  (find-lib* "json" nil)
  (find-lib "time")
  (find-lib "scheduler")
  (find-lib* "tools.deps.alpha" {})
  (find-lib* "aws" nil)
  )

(defn find-revs
  "Given a lib symbol, return coll of coords for versions in Maven, newest first.
  Given a git url, return coll of coords with sha and shas associated with tags, descendant first"
  [lib-or-git-url]
  (cond
    (qualified-symbol? lib-or-git-url)
    (let [{:keys [mvn/repos mvn/local-repo]} (current-basis)
          local-repo (or local-repo maven/default-local-repo)
          system ^RepositorySystem (session/retrieve :mvn/system #(maven/make-system))
          session ^RepositorySystemSession (session/retrieve :mvn/session #(maven/make-session system local-repo))
          artifact (maven/coord->artifact lib-or-git-url {:mvn/version "[0.0.1,)"})
          req (VersionRangeRequest. artifact (maven/remote-repos repos) nil)
          result (.resolveVersionRange system session req)]
      (if result
        (map #(.toString ^Object %) (reverse (.getVersions result)))))

    (string? lib-or-git-url)
    nil))

(comment
  (find-revs 'org.clojure/core.async)
  )