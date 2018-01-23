;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.util.io
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as jio])
  (:import
    [java.io FileReader PushbackReader]))

(defn printerrln
  "println to *err*"
  [& msgs]
  (binding [*out* *err*]
    (apply println msgs)))

(defn slurp-edn
  "Read the edn file specified by f, a string or File.
  An empty file will return nil."
  [f]
  (let [EOF (Object.)
        fi (jio/file f)]
    (with-open [rdr (PushbackReader. (FileReader. fi))]
      (let [val (edn/read {:eof EOF} rdr)]
        (if (identical? val EOF)
          nil
          val)))))
