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
  {:aliases {:foo {:fn 'clojure.core/count :args {:x 1}}}}
  #_(when-let [f (jio/file (System/getProperty "clojure.basis"))]
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
    (cond
      (zero? arg-count) fread
      (even? arg-count) (assoc fread :overrides (mapv edn/read-string args))
      :else (throw (ex-info (str "Key is missing value: " (last args)) {})))))

(defn- fill-alias
  [{:keys [alias] :as m}]
  (if alias
    (do
      (println (-> (read-basis) (get-in [:aliases alias])))
      (println (-> (read-basis) (get-in [:aliases alias]) (select-keys [:fn :args])))
      (merge m (-> (read-basis) (get-in [:aliases alias]) (select-keys [:fn :args]))))
    m))

(defn- apply-overrides
  [args overrides]
  (reduce (fn [m [k v]]
            (if (sequential? k)
              (assoc-in m k v)
              (assoc m k v)))
    args (partition 2 overrides)))

(defn exec
  "Resolve and execute the function f (a symbol) with a map of args"
  [f args]
  ;(println "Executing" f "with" args)
  ((requiring-resolve f) args))

(defn -main
  [& args]
  (let [{f :fn :keys [alias args overrides]} (-> args parse-args fill-alias)
        ;_ (println "fn" f "alias" alias "overrides" overrides)
        fargs (apply-overrides args overrides)]
    (exec f fargs)))

(comment
  (-main "-X:foo" "[:y :z]" "1")
  (-main "-Fclojure.core/prn" "[:y :z]" "1" ":x" "2" )
  (apply-overrides {:x 1} [:x 2])
  )