;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.run
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
    (nil? arg) (throw (ex-info "No args passed to run" {}))
    (= "-X" arg) nil
    (str/starts-with? arg "-X") (let [fread (edn/read-string (subs arg 2))]
                                  (if (or (keyword? fread) (qualified-symbol? fread))
                                    fread
                                    (throw (ex-info (str "Invalid first arg to run: " arg) {}))))
    :else (throw (ex-info (str "Invalid first arg to run: " arg) {}))))

(defn- parse-key
  [s]
  (vec (parse/parse-kws s)))

(defn- parse-kvs
  [args]
  (when-not (even? (count args))
    (throw (ex-info (apply str "Key is missing value: " (last args)) {})))
  (reduce (fn [m [k v]] (assoc-in m (parse-key k) (edn/read-string v)))
    {} (partition 2 args)))

(defn parse-args
  [[arg & args]]
  (let [fread (check-first arg)
        arg-count (count args)]
    (cond
      (nil? fread)
      (throw (ex-info "Must specify either fully-qualified symbol to invoke or alias name with -X" {}))

      (symbol? fread)
      (cond
        (zero? arg-count) {:fname fread}
        (even? arg-count) {:fname fread, :args (-> args parse-kvs)}
        :else (cond-> {:fname fread
                       :alias (-> args first edn/read-string)}
                (> arg-count 1) (assoc :args (-> args rest parse-kvs))))

      (keyword? fread)
      (if (odd? arg-count)
        (throw (ex-info (str "Key is missing value: " (last args)) {}))
        (cond-> {:alias fread}
          (and (pos? arg-count) (even? arg-count)) (assoc :args (parse-kvs args)))))))

(defn- deep-merge
  [m1 m2]
  (cond
    (and (map? m1) (map? m2)) (merge-with deep-merge m1 m2)
    (and (map? m1) (nil? m2)) m1
    :else m2))

(defn -main
  [& args]
  (let [{:keys [fname alias args]} (parse-args args)
        ;_ (println "fname" fname "alias" alias "args" args)
        basis (read-basis)
        config-args (get-in basis [:aliases alias :run-args])
        config-args (if (keyword? config-args) (get-in basis [:aliases config-args]) config-args)
        f (or fname (get-in basis [:aliases alias :run-fn]))
        fargs (deep-merge config-args args)]
    ;(println "config-args" config-args)
    ;(println "f" f)
    ;(println "fargs" fargs)
    ;(println)
    ((requiring-resolve f) fargs)))
