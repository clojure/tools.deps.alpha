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
    (require 'clojure.run.exec)
    (let [nsd (some-> "clojure.run.exec/*ns-default*" symbol resolve deref)
          nsa (some-> "clojure.run.exec/*ns-aliases*" symbol resolve deref)]
      (cond-> {}
        nsd (assoc :ns-default nsd)
        nsa (assoc :ns-aliases nsa)))
    (catch Exception e
      (throw (ex-info (.getMessage e) {} e)))))

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
  [{:keys [ns fn] :as args}]
  (let [{:keys [ns-default ns-aliases]} (merge args (garner-ns-defaults))]
    (if fn
      (#'repl/print-doc (meta (requiring-resolve (qualify-fn fn ns-aliases ns-default))))
      (let [ns-maybe (or ns ns-default)
            ns (if ns-aliases (get ns-aliases ns-maybe ns-maybe) ns-maybe)]
        (when (nil? ns)
          (throw (ex-info "No namespace or function specified" {})))
        (require ns)
        (let [my-ns (the-ns ns)
              ns-doc (:doc (meta my-ns))]
          ;; Print namespace docs
          (when (not (str/blank? ns-doc))
            (println ns-doc)
            (println))
          ;; Print function docs
          (doseq [[k v] (->> my-ns ns-publics (sort-by key))]
            (when (instance? clojure.lang.Fn @v)
              (#'repl/print-doc (meta v)))))))))

(defn dir
  "Prints a sorted directory of public vars in a namespace. If a namespace is not
  specified :ns-default is used instead."
  [{:keys [ns] :as args}]
  (let [{:keys [ns-default ns-aliases]} (merge args (garner-ns-defaults))
        ns-maybe (or ns ns-default)
        ns (if ns-aliases (get ns-aliases ns-maybe ns-maybe) ns-maybe)
        _ (require ns)
        my-ns (the-ns ns)]
    (doseq [[s v] (->> my-ns ns-publics (sort-by key))]
      (when (instance? clojure.lang.Fn @v)
        (println s)))))

(comment
  (doc {:ns 'clojure.tools.cli.help})
  (doc {:fn 'clojure.tools.cli.help/doc})
  (dir {:ns 'clojure.tools.cli.help})
  )
