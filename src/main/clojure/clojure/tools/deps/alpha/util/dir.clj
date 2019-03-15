;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.util.dir
  (:require
    [clojure.java.io :as jio])
  (:import
    [java.io File]))

(set! *warn-on-reflection* true)

(def ^:dynamic *the-dir*
  "Thread-local directory context for resolving relative directories.
  Defaults to current directory. Should always hold an absolute directory
  java.io.File, never null."
  (jio/file (System/getProperty "user.dir")))

(defn as-canonical
  "As canonical File in terms of the current directory context"
  ^File [^File dir]
  (.getCanonicalFile
    (if (.isAbsolute dir)
      dir
      (File. ^File *the-dir* (.getPath dir)))))

(defmacro with-dir
  "Push directory into current directory context for execution of body."
  [^File dir & body]
  `(binding [*the-dir* (as-canonical ~dir)]
     ~@body))

(defn canonicalize
  "Make canonical File in terms of the current directory context.
  f may be either absolute or relative."
  ^File [^File f]
  (.getCanonicalFile
    (if (.isAbsolute f)
      f
      (jio/file *the-dir* f))))