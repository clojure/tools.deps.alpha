;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.extensions.git
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.util.io :refer [printerrln]])
  (:import
    [java.io File]
    [org.eclipse.jgit.api Git GitCommand TransportCommand TransportConfigCallback]
    [org.eclipse.jgit.lib Repository RepositoryBuilder ObjectId]
    [org.eclipse.jgit.revwalk RevWalk]
    [org.eclipse.jgit.transport SshTransport JschConfigSessionFactory]
    [org.eclipse.jgit.errors MissingObjectException]
    [com.jcraft.jsch JSch]
    [com.jcraft.jsch.agentproxy Connector ConnectorFactory RemoteIdentityRepository]))

;;;; Git

(def ^:private ^TransportConfigCallback ssh-callback
  (delay
    (let [factory (doto (ConnectorFactory/getDefault) (.setPreferredUSocketFactories "jna,nc"))
          connector (.createConnector factory)]
      (JSch/setConfig "PreferredAuthentications" "publickey")
      (reify TransportConfigCallback
        (configure [_ transport]
          (.setSshSessionFactory ^SshTransport transport
            (proxy [JschConfigSessionFactory] []
              (configure [host session])
              (getJSch [hc fs]
                (doto (proxy-super getJSch hc fs)
                  (.setIdentityRepository (RemoteIdentityRepository. connector)))))))))))

(defn- call-with
  [^String url ^GitCommand command]
  (if (and (instance? TransportCommand command)
        (not (str/starts-with? url "http")))
    (.. ^TransportCommand command (setTransportConfigCallback @ssh-callback) call)
    (.call command)))

(defn- clean-url
  "Chop leading protocol, trailing .git, replace :'s with /"
  [url]
  (-> url
    (str/split #"://")
    last
    (str/replace #"\.git$" "")
    (str/replace #":" "/")))

(defn- git-repo
  ^Repository [{:keys [^File git-dir ^File rev-dir]}]
  (.. (cond-> (RepositoryBuilder.)
        git-dir (.setGitDir git-dir)
        rev-dir (.setWorkTree rev-dir))
    build))

(defn- git-fetch
  ^Git [dirs ^String url]
  (let [git (Git. (git-repo dirs))]
    (call-with url (.. git fetch))
    git))

;; TODO: restrict clone to an optional refspec?
(defn- git-clone-bare
  ^File [^String url ^File git-dir]
  (call-with url
    (.. (Git/cloneRepository) (setURI url) (setGitDir ^File git-dir)
      (setBare true)
      (setNoCheckout true)
      (setCloneAllBranches true)))
  git-dir)

(defn- git-checkout
  [^Git git ^String rev ^String url]
  (call-with url (.. git checkout (setStartPoint rev) (setAllPaths true))))

(defn- full-commit
  ^String [^File git-dir ^String rev]
  (if (ObjectId/isId rev)
    rev
    (.. (git-repo {:git-dir git-dir}) (resolve rev) getName)))

(defn- parent?
  [^String parent ^String child dirs]
  (let [repo (git-repo dirs)
        walk (RevWalk. repo)]
    (try
      (let [child-commit (.lookupCommit walk (ObjectId/fromString child))
            parent-commit (.lookupCommit walk (ObjectId/fromString parent))]
        (.isMergedInto walk parent-commit child-commit))
      (catch MissingObjectException e false)
      (finally (.dispose walk)))))

(defn- ensure-git-dir
  "Download the bare git dir for the specified url, return the cached git dir"
  ^File [^String cache-dir ^String url]
  (let [git-dir (jio/file cache-dir "_repos" (clean-url url))]
    (if (.exists git-dir)
      git-dir
      (git-clone-bare url git-dir))))

(defn- ensure-rev-dir
  "Download working tree for the specified url, return the cached rev dir"
  [lib ^String cache-dir ^String url ^String rev]
  (let [git-dir (ensure-git-dir cache-dir url)
        rev-dir (jio/file cache-dir "libs" (namespace lib) (name lib) rev)
        dirs {:git-dir git-dir, :rev-dir rev-dir}]
    (when (not (.exists rev-dir))
      (git-checkout (git-fetch dirs url) rev url))
    dirs))

;;;; Extension methods

(defmethod ext/canonicalize :git
  [lib {:keys [git/url rev] :as coord} config]
  (let [cache-dir (-> config :git/config :cache-dir)
        git-dir (ensure-git-dir cache-dir url)
        sha (full-commit git-dir rev)]
    [lib (assoc coord :rev sha)]))

(defmethod ext/dep-id :git
  [lib coord config]
  (select-keys coord [:git/url :rev]))

(defmethod ext/manifest-type :git
  [lib {:keys [git/url rev deps/manifest] :as coord} config]
  (let [cache-dir (-> config :git/config :cache-dir)
        {:keys [rev-dir]} (ensure-rev-dir lib cache-dir url rev)]
    (if manifest
      {:deps/manifest manifest, :deps/root rev-dir}
      (ext/detect-manifest rev-dir))))

;; 0 if x and y are the same commit
;; negative if x is parent of y (y derives from x)
;; positive if y is parent of x (x derives from y)
(defmethod ext/compare-versions [:git :git]
  [lib {x-url :git/url, x-rev :rev :as x} {y-url :git/url, y-rev :rev :as y} config]
  (let [cache-dir (-> config :git/config :cache-dir)]
    (cond
      (= x-rev y-rev) 0
      (parent? x-rev y-rev (ensure-rev-dir lib cache-dir y-url y-rev)) -1
      (parent? y-rev x-rev (ensure-rev-dir lib cache-dir x-url x-rev)) 1
      :else (throw (ex-info "No known relationship between git versions" {:x x :y y})))))

(comment
  (def dirs (#'ensure-rev-dir 'org.clojure/spec.alpha
              (File. "/Users/alex/code/.clojure/.cpcache")
              "https://github.com/clojure/spec.alpha.git"
              "739c1af56dae621aedf1bb282025a0d676eff713"))

  (ext/compare-versions
    'org.clojure/spec.alpha
    {:git/url "https://github.com/clojure/spec.alpha.git" :rev "739c1af56dae621aedf1bb282025a0d676eff713"}
    {:git/url "git@github.com:clojure/spec.alpha.git" :rev "a65fb3aceec67d1096105cab707e6ad7e5f063af"}
    {:git/config {:cache-dir "./gitlibs"}})
  )