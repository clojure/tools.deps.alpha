;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.exec
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.script.parse :as parse]))

(defn- read-basis
  []
  (when-let [f (jio/file (System/getProperty "clojure.basis"))]
    (if (and f (.exists f))
      (deps/slurp-deps f)
      (throw (IllegalArgumentException. "No basis declared in clojure.basis system property")))))

(defn- check-first
  [arg]
  (cond
    (nil? arg) (throw (ex-info "No args passed to exec" {}))
    (= "-X" arg) (throw (ex-info "No alias specified with -X" {}))
    (= "-F" arg) (throw (ex-info "No function specified with -F" {}))
    (str/starts-with? arg "-X") (let [fread (edn/read-string (subs arg 2))]
                                  (if (keyword? fread)
                                    {:alias fread}
                                    (throw (ex-info (str "Invalid first arg to exec: " arg) {}))))
    (str/starts-with? arg "-F") (let [fread (edn/read-string (subs arg 2))]
                                  (if (qualified-symbol? fread)
                                    {:fn fread}
                                    (throw (ex-info (str "Invalid first arg to exec: " arg) {}))))
    :else (throw (ex-info (str "Invalid first arg to exec: " arg) {}))))

(defn- parse-args
  [[arg & args]]
  (let [fread (check-first arg)
        arg-count (count args)]
    (cond-> fread
      (seq args) (assoc :overrides (mapv edn/read-string args)))))

(defn exec
  "Resolve and execute the function f (a symbol) with args"
  [f & args]
  (apply (requiring-resolve f) args))

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
  (let [{f :fn, args :args} (get-in (read-basis) [:aliases alias])]
    (exec f (apply-overrides args overrides))))

(defn -main
  [& args]
  (let [{:keys [alias overrides] :as parsed} (parse-args args)]
    (if alias
      (exec-alias alias overrides)
      (apply exec (:fn parsed) overrides))))

(comment
  (-main "-X:foo" "[:y :z]" "1")
  (-main "-X:foo" ":bar")
  (-main "-Fclojure.core/prn" "{:y 1}" ":foo")
  (apply-overrides {:x 1} [:x 2 [:y :z] 3])
  )