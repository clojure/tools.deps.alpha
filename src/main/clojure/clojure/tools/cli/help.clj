;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.cli.help
  (:require
    [clojure.string :as str]
    [clojure.repl :as repl]))

(defn doc
  "Print doc for the specified namespace or function. If neither is specified, print docs
  for :ns-default.

  Options:
    :ns Print docs for namespace
    :fn Print docs for function"
  [{:keys [ns fn]}]
  (if fn
    (#'repl/print-doc (meta (resolve fn))) ;; TODO - resolve with :ns-default+:ns-aliases
    (let [ns (or ns :TODO)] ;; TODO - default to :ns-default
      (let [my-ns (the-ns ns)
            ns-doc (:doc (meta my-ns))]
        ;; Print namespace docs
        (when (not (str/blank? ns-doc))
          (println ns-doc)
          (println))
        ;; Print function docs
        (doseq [v (->> my-ns ns-publics (sort-by key) (map val))]
          (#'repl/print-doc (meta v)))))))

(comment
  (doc {:ns 'clojure.tools.cli.help})
  (doc {:fn 'clojure.tools.cli.help/doc})
  )