;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.extensions.faken
  (:require
    [clojure.string :as str]
    [clojure.tools.deps.alpha.extensions :as ext])
  (:import
    ;; maven-resolver-util
    [org.eclipse.aether.util.version GenericVersionScheme]))

;; Fake Maven extension for testing dependency resolution

;; Use the functions to construct a faux Maven repo

;; {lib {coord [dep1 ...]}}
(def ^:dynamic repo {})

(defmacro with-libs
  [libs & body]
  `(binding [repo ~libs]
     ~@body))

(defmethod ext/dep-id :fkn
  [_lib coord _config]
  (select-keys coord [:fkn/version]))

(defmethod ext/manifest-type :fkn
  [_lib _coord _config]
  {:deps/manifest :fkn})

(defonce ^:private version-scheme (GenericVersionScheme.))

(defn- parse-version [{version :fkn/version :as _coord}]
  (.parseVersion ^GenericVersionScheme version-scheme ^String version))

(defmethod ext/compare-versions [:fkn :fkn]
  [_lib coord-x coord-y _config]
  (apply compare (map parse-version [coord-x coord-y])))

(defmethod ext/coord-deps :fkn
  [lib coord _manifest config]
  (get-in repo [lib (ext/dep-id lib coord config)]))

(defn make-path
  [lib {:keys [fkn/version]}]
  (let [[n c] (str/split (name lib) #"\$")]
    (str "REPO/" (namespace lib)
         "/" n
         "/" version
         "/" n (if c (str "-" c) "") "-" version ".jar")))

(defmethod ext/coord-paths :fkn
  [lib coord _manifest _config]
  [(make-path lib coord)])

(comment
  (with-libs
    {'a/a {{:fkn/version "0.1.2"} [['b/b {:fkn/version "1.2.3"}]]}
     'b/b {{:fkn/version "1.2.3"} nil}}
    (ext/coord-deps 'a/a {:fkn/version "0.1.2"} :fkn nil))
  )