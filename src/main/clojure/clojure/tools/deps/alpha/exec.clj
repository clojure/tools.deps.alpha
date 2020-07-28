;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.exec
  (:require
    ;; NOTE: ONLY depend on Clojure core, loaded in user's classpath so can't have any deps
    [clojure.edn :as edn]
    [clojure.java.io :as jio]
    [clojure.string :as str]))

(defn- read-basis
  []
  (when-let [f (jio/file (System/getProperty "clojure.basis"))]
    (if (and f (.exists f))
      (-> f slurp edn/read-string)
      (throw (IllegalArgumentException. "No basis declared in clojure.basis system property")))))

(defn- check-first
  [arg]
  (cond
    (nil? arg) (throw (ex-info "No args passed to exec" {}))
    (= "-X" arg) (throw (ex-info "No alias specified with -X" {}))
    (str/starts-with? arg "-X") (let [fread (edn/read-string (subs arg 2))]
                                  (if (keyword? fread)
                                    {:alias fread}
                                    (throw (ex-info (str "Invalid first arg to exec: " arg) {}))))
    :else (throw (ex-info (str "Invalid first arg to exec: " arg) {}))))

(defn- parse-args
  [[arg & args]]
  (let [fread (check-first arg)
        arg-count (count args)]
    (cond-> fread
      (seq args) (assoc :overrides (mapv edn/read-string args)))))

(defn- requiring-resolve'
  ;; copied and modified from core to remove constraints on Clojure 1.10.x
  [sym]
  (if (nil? (namespace sym))
    (throw (IllegalArgumentException. (str "Not a qualified symbol: " sym)))
    (or (resolve sym)
      (do
        (-> sym namespace symbol require)
        (resolve sym)))))

(defn exec
  "Resolve and execute the function f (a symbol) with args"
  [f & args]
  (apply (requiring-resolve' f) args))

(defn- apply-overrides
  [args overrides]
  (reduce (fn [m [k v]]
            (if (sequential? k)
              (assoc-in m k v)
              (assoc m k v)))
    args (partition-all 2 overrides)))

(defn- exec-alias
  [alias overrides]
  (when (odd? (count overrides))
    (throw (ex-info (str "Key is missing value: " (last overrides)) {})))
  (let [basis (read-basis)
        {f :fn, maybe-args :args} (get-in basis [:aliases alias])
        args (if (keyword? maybe-args) (get-in basis [:aliases maybe-args]) maybe-args)]
    ;(println "args" args)
    ;(println "overrides" overrides)
    (exec f (apply-overrides args overrides))))

(defn -main
  [& args]
  (let [{:keys [alias overrides] :as parsed} (parse-args args)]
    (exec-alias alias overrides)))