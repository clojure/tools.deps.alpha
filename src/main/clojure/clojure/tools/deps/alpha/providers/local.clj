;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.providers.local
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as jio]
    [clojure.tools.deps.alpha.providers :as providers]))

(defmethod providers/expand-dep :local
  [lib {:keys [local/root] :as coord} config]
  (assert (not (nil? path)) (format "Local coordinate for %s is missing :local/root" lib))
  (let [path-file (jio/file path)]
    ;; TODO
    coord))

(defmethod providers/download-dep :local
  [lib coord config]
  (assoc coord :path (:local/root coord)))

