;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.alpha.script.print-tree
  (:require
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]]
    [clojure.tools.cli :as cli])
  (:import
    [clojure.lang IExceptionInfo]))

(def ^:private opts
  [[nil "--libs-file PATH" "Libs cache file to write"]])

(defn run
  "Run print-tree script. See -main for details."
  [{:keys [libs-file] :as _options}]
  (let [lib-map (io/slurp-edn libs-file)]
    (deps/print-tree lib-map)))

(defn -main
  "Main entry point for print-tree script.

  Required:
    --libs-file=path - libs file

  Prints the tree from libs file"
  [& args]
  (try
    (let [{:keys [options errors]} (cli/parse-opts args opts)]
      (when (seq errors)
        (run! println errors)
        (System/exit 1))
      (run options))
    (catch Throwable t
      (printerrln "Error printing tree." (.getMessage t))
      (when-not (instance? IExceptionInfo t)
        (.printStackTrace t))
      (System/exit 1))))

(comment
  (run {:libs-file "foo.libs"})
  )
