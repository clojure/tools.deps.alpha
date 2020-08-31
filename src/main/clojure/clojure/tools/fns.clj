;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.fns
  (:require [clojure.java.io :as jio]
            [clojure.edn :as edn]
            [clojure.tools.deps.alpha :as deps]))

(defn- read-basis
  "Read runtime and return the runtime basis"
  []
  (-> (System/getProperty "clojure.basis") jio/file slurp edn/read-string))

(defn tree
  "Print deps tree. Takes an opts map, but no opts yet."
  [_]
  (let [{:keys [libs]} (read-basis)]
    (deps/print-tree libs)))