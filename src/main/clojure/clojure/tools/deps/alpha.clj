;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.providers :as providers])
  (:import
    [clojure.lang PersistentQueue]
    [java.io File]))

(defn- expand-deps
  [deps default-deps override-deps config verbose]
  (loop [q (into (PersistentQueue/EMPTY) (map vector deps)) ;; queue of dep paths
         tree {} ;; map of [lib coord] to child map of same, leaf values are nil
         seen #{}] ;; set of [lib coord]
    (if-let [path (peek q)] ;; path from root dep to dep being expanded
      (let [q' (pop q)
            [lib coord :as dep] (peek path)
            parents (pop path)
            override-coord (get override-deps lib)
            use-coord (cond override-coord override-coord
                            coord coord
                            :else (get default-deps lib))]
        (if (seen dep)
          (recur q' tree seen)
          (let [use-path (conj parents [lib use-coord])
                children (providers/expand-dep lib use-coord config)
                child-paths (map #(conj use-path %) children)]
            (when verbose (println "Expanding" lib use-coord))
            (recur (into q' child-paths) (update-in tree use-path merge nil) (conj seen dep)))))
      (do
        (when verbose (println) (println "Expanded tree:") (pprint tree))
        tree))))

(defn- cut-exclusions
  ([deps-tree]
    (cut-exclusions deps-tree #{}))
  ([deps-tree parent-exclusions]
   (let [node (->> deps-tree
                (remove (fn [[[lib coord] _children]] (contains? parent-exclusions lib)))
                (map (fn [[[lib {:keys [exclusions] :as coord}] children]]
                       [[lib (dissoc coord :exclusions)]
                        (cut-exclusions children (into parent-exclusions exclusions))])))]
     (if (empty? node)
       nil
       (into {} node)))))

(defn- choose-coord
  [coord1 coord2 top-coord]
  (cond
    top-coord top-coord
    (and coord1 (not coord2)) coord1
    (and coord2 (not coord1)) coord2
    :else (if (pos? (providers/compare-versions coord1 coord2)) coord1 coord2)))

(defn- resolve-versions
  [deps-tree config verbose]
  (let [top-deps (into {} (keys deps-tree))]
    (loop [q (into (PersistentQueue/EMPTY) deps-tree)
           lib-map {}]
      (if-let [[[lib coord] child-deps] (peek q)]
        (recur
          (into (pop q) (map #(update-in % [0 1 :dependents] (fnil conj []) lib) child-deps))
          (assoc lib-map lib (choose-coord (lib-map lib) coord (get top-deps lib))))
        (do
          (when verbose
            (println)
            (println "Resolved libs:")
            (pprint lib-map))
          lib-map)))))

(defn- download-deps
  [lib-map config]
  (reduce (fn [ret [lib coord]]
            (assoc ret lib (providers/download-dep lib coord config)))
    {} lib-map))

(defn resolve-deps
  [{:keys [deps] :as deps-map} args-map]
  (let [{:keys [extra-deps default-deps override-deps verbose]} args-map
        deps (merge (:deps deps-map) extra-deps)]

    (when verbose
      (println "Initial deps to expand:")
      (pprint deps))

    (-> deps
      (expand-deps default-deps override-deps deps-map verbose)
      (cut-exclusions)
      (resolve-versions deps-map verbose)
      (download-deps deps-map))))

(defn make-classpath
  [lib-map paths {:keys [classpath-overrides extra-paths] :as classpath-args}]
  (let [libs (merge-with (fn [coord path] (assoc coord :path path)) lib-map classpath-overrides)
        lib-paths (map :path (vals libs))]
    (str/join File/pathSeparator (concat paths extra-paths lib-paths))))

(comment
  (require
    '[clojure.tools.deps.alpha.providers.maven :as mvn]
    '[clojure.tools.deps.alpha.providers.file])

  (expand-deps {'org.clojure/clojure {:mvn/version "1.9.0-alpha17"}} nil nil
    {:mvn {:repos mvn/standard-repos}} true)

  (expand-deps {'org.clojure/clojure {:mvn/version "1.9.0-alpha17"}
                'org.clojure/core.memoize {:mvn/version "0.5.8"}} nil nil
    {:mvn {:repos mvn/standard-repos}} true)

  (clojure.pprint/pprint
    (resolve-deps {:deps {'org.clojure/clojure {:mvn/version "1.8.0"}
                          'org.clojure/core.memoize {:mvn/version "0.5.8"}}
                   :mvn/repos mvn/standard-repos} nil))

  (make-classpath
    (resolve-deps {:deps {'org.clojure/clojure {:mvn/version "1.9.0-alpha17"}
                          'org.clojure/core.memoize {:mvn/version "0.5.8"}}
                   :mvn/repos mvn/standard-repos} nil) ["src"] {:extra-paths ["test"]})

  (clojure.pprint/pprint
    (resolve-deps {:deps {'org.clojure/tools.analyzer.jvm {:mvn/version "0.6.9"}}
                   :mvn/repos mvn/standard-repos} {:verbose true}))

  (clojure.pprint/pprint
    (resolve-deps {:deps {'cheshire {:mvn/version "5.7.0"}}
                   :mvn/repos mvn/standard-repos} nil))

  ;; top deps win
  (clojure.pprint/pprint
    (resolve-deps {:deps {'org.clojure/clojure {:mvn/version "1.2.0"}
                          'cheshire/cheshire {:mvn/version "5.8.0"}}
                   :mvn/repos mvn/standard-repos} nil))

  ;; override to local
  (make-classpath
    (resolve-deps
      {:deps {'org.clojure/core.memoize {:mvn/version "0.5.8"}}
       :mvn/repos mvn/standard-repos}
      {:override-deps {'org.clojure/clojure {:mvn/version "1.3.0"}} ;; is this working?
       :verbose true})
    ["src"] nil)

  (make-classpath
    (resolve-deps
      {:deps {'org.clojure/tools.deps.alpha {:mvn/version "0.1.40"}
              'org.clojure/clojure {:mvn/version "1.9.0-alpha17"}}
       :mvn/repos mvn/standard-repos} nil) nil nil)

  ;; classpath overrides
  (make-classpath
    (resolve-deps
      {:deps {'org.clojure/tools.deps.alpha {:mvn/version "0.1.40"}
              'org.clojure/clojure {:mvn/version "1.9.0-alpha17"}}
       :mvn/repos mvn/standard-repos} nil) nil
    '{:classpath-overrides {org.clojure/clojure "foo"}})

  (make-classpath
    (resolve-deps
      {:deps {'org.clojure/clojure {:mvn/version "1.8.0"}}
       :mvn/repos mvn/standard-repos}
      {:verbose true})
    nil
    {'org.clojure/clojure "/Users/alex/code/clojure/target/classes"})

  ;; err case
  (resolve-deps {:deps {'bogus {:mvn/version "1.2.3"}}
                 :mvn/repos mvn/standard-repos} nil)
  )
