;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.providers.local
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.deps.alpha.providers :as providers]))

(defmethod providers/dep-id :local
  [lib {:keys [local/root] :as coord}]
  {:lib lib
   :root root})

(defmethod providers/manifest-type :local
  [lib {:keys [local/root deps/manifest] :as coord} config]
  (cond
    manifest {:deps/manifest manifest :deps/root root}
    (.isFile (jio/file root)) {:deps/manifest :jar, :deps/root root}
    :else (providers/detect-manifest root)))

(defmethod providers/coord-deps :jar
  [lib {:keys [local/root] :as coord} config]
  [])

(defmethod providers/coord-paths :jar
  [lib coord config]
  [(:local/root coord)])

