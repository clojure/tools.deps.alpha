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

(defn- garner-ns-defaults []
  (try
    (let [nsd (-> "clojure.run.exec/*ns-default*" symbol resolve deref)
          nsa (-> "clojure.run.exec/*ns-aliases*" symbol resolve deref)]
      (require 'clojure.run.exec)
      (require nsd)
      {:ns-default nsd
       :ns-aliases nsa})
    (catch RuntimeException _
      {})))

(defn- qualify-fn
  "Compute function symbol based on exec-fn, ns-aliases, and ns-default"
  [fsym ns-aliases ns-default]
  ;; validation - make specs?
  (when (and fsym (not (symbol? fsym)))
    (throw (ex-info (str "Expected function symbol:" fsym) {})))

  (when fsym
    (if (qualified-ident? fsym)
      (let [nsym (get ns-aliases (symbol (namespace fsym)))]
        (if nsym
          (symbol (str nsym) (name fsym))
          fsym))
      (if ns-default
        (symbol (str ns-default) (str fsym))
        (throw (ex-info (str "Unqualified function can't be resolved:" fsym) {}))))))

(defn doc
  "Print doc for the specified namespace or function. If neither is specified, print docs
  for :ns-default.

  Options:
    :ns Print docs for namespace
    :fn Print docs for function"
  [{:keys [ns fn]}]
  (let [{:keys [ns-default ns-aliases]} (garner-ns-defaults)]
    (if fn
      (#'repl/print-doc (meta (resolve (qualify-fn fn ns-aliases ns-default)))) ;; TODO - resolve with :ns-default+:ns-aliases
      (let [ns (or ns ns-default)]
        (let [my-ns (the-ns ns)
              ns-doc (:doc (meta my-ns))]
          ;; Print namespace docs
          (when (not (str/blank? ns-doc))
            (println ns-doc)
            (println))
          ;; Print function docs
          (doseq [v (->> my-ns ns-publics (sort-by key) (map val))]
            (#'repl/print-doc (meta v))))))))

(comment
  (doc {:ns 'clojure.tools.cli.help})
  (doc {:fn 'clojure.tools.cli.help/doc})
  )
