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
    [clojure.tools.deps.alpha.extensions :as ext]

    ;; Load extensions
    [clojure.tools.deps.alpha.extensions.maven]
    [clojure.tools.deps.alpha.extensions.local]
    [clojure.tools.deps.alpha.extensions.git]
    [clojure.tools.deps.alpha.extensions.deps]
    [clojure.tools.deps.alpha.extensions.pom])
  (:import
    [clojure.lang PersistentQueue]
    [java.io File]))

(def ^:private merge-alias-rules
  {:extra-deps merge
   :override-deps merge
   :default-deps merge
   :classpath-overrides merge
   :extra-paths (comp vec distinct concat)
   :jvm-opts (comp vec concat)
   :main-opts (comp last #(remove nil? %) vector)
   :verbose #(or %1 %2)})

(defn- choose-rule [alias-key]
  (or (merge-alias-rules alias-key)
    (throw (ex-info (format "Unknown alias key: %s" alias-key) {:key alias-key}))))

(defn- merge-alias-maps
  "Like merge-with, but using custom per-alias-key merge function"
  [& ms]
  (reduce
    #(reduce
       (fn [m [k v]] (update m k (choose-rule k) v))
       %1 %2)
    {} ms))

(defn- check-aliases
  "Check that all aliases are known and error if aliases are undeclared"
  [deps aliases]
  (if-let [unknown (seq (remove #(contains? (:aliases deps) %) aliases))]
    (throw (ex-info (str "Specified aliases are undeclared: " (vec unknown))
             {:aliases (vec unknown), :available (-> deps :aliases keys vec)}))
    aliases))

(defn combine-aliases
  "Find, read, and combine alias maps identified by alias keywords from
  a deps configuration into a single args map."
  [deps alias-kws]
  (->> alias-kws
    (check-aliases deps)
    (map #(get-in deps [:aliases %]))
    (apply merge-alias-maps)))

(defn lib-location
  "Find the file path location of where a lib/coord would be located if procured
  without actually doing the procuring!"
  [lib coord deps-config]
  (ext/lib-location lib coord deps-config))

(defn- canonicalize-deps
  [deps config]
  (reduce
    (fn [m [lib coord]] (conj m (ext/canonicalize lib coord config)))
    [] deps))

;; exclusions tree

(defn- add-exclusion
  [exclusions path libs]
  (assoc exclusions path (set libs)))

(defn- excluded?
  [exclusions path lib]
  (let [lib-name (first (str/split (name lib) #"\$"))
        base-lib (symbol (namespace lib) lib-name)]
    (loop [search path]
      (when (seq search)
        (if (get-in exclusions [search base-lib])
          true
          (recur (pop search)))))))

;; version map

;; {lib {:versions {coord-id coord}     ;; all version coords
;;       :paths    {coord-id #{paths}}  ;; paths to coord-ids
;;       :select   coord-id             ;; current selection
;;       :pin      true}                ;; if selection is pinned

(defn- parent-missing?
  [vmap lib path]
  (when (seq path)
    (let [parent-lib (last path)
          parent-path (vec (butlast path))
          {:keys [paths select]} (get vmap parent-lib)]
      (not (contains? (get paths select) parent-path)))))

(defn- include-coord?
  [vmap lib coord coord-id path exclusions verbose]
  (cond
    ;; lib is a top dep and this is it => select and pin
    (empty? path) :pin

    ;; lib is excluded in this path => omit
    (excluded? exclusions path lib)
    (do (when verbose (println "\t=> excluded"))
        nil)

    ;; lib is a top dep and this isn't it => omit
    (get-in vmap [lib :pin])
    (do (when verbose (println "\t=> skip, top dep used instead"))
        nil)

    ;; lib's parent path is not included => omit
    (parent-missing? vmap lib path)
    (do (when verbose (println "\t=> skip, path to dep no longer included" path))
        nil)

    ;; otherwise => choose newest version
    :else :choice))

(defn- dominates?
  [lib new-coord old-coord config]
  (pos? (ext/compare-versions lib new-coord old-coord config)))

(defmacro ^:private with-log
  [verbose msg & body]
  `(do
     (when ~verbose (println "\t=> " ~msg))
     ~@body))

(defn- add-coord
  [vmap lib coord-id coord path action config verbose]
  (let [vmap' (-> (or vmap {})
                (assoc-in [lib :versions coord-id] coord)
                (update-in [lib :paths]
                  (fn [coord-paths]
                    (merge-with into {coord-id #{path}} coord-paths))))]
    (if (= action :pin)
      (with-log verbose "include, pin top dep"
        (update-in vmap' [lib] merge {:select coord-id :pin true}))
      (let [select-id (get-in vmap' [lib :select])]
        (if (not select-id)
          (with-log verbose "include, new dep"
            (assoc-in vmap' [lib :select] coord-id))
          (let [select-coord (get-in vmap' [lib :versions select-id])]
            (cond
              (= select-id coord-id)
              (with-log verbose "skip, same as current selection")

              (dominates? lib coord select-coord config)
              (with-log verbose (str "include, replace" select-id)
                (assoc-in vmap' [lib :select] coord-id))

              :else
              (with-log verbose (str "current is newer" select-id)))))))))

;; expand-deps

(defn- expand-deps
  [deps default-deps override-deps config verbose]
  (loop [q (into (PersistentQueue/EMPTY) (map vector deps))
         version-map nil
         exclusions nil] ;; path to set of exclusions at path
    (if-let [path (peek q)] ;; path from root dep to dep being expanded
      (let [q' (pop q)
            [lib coord] (peek path)
            parents (pop path)
            override-coord (get override-deps lib)
            use-coord (cond override-coord override-coord
                            coord coord
                            :else (get default-deps lib))
            coord-id (ext/dep-id lib use-coord config)]
        (when verbose (println "Expanding" lib coord-id))
        (if-let [action (include-coord? version-map lib use-coord coord-id parents exclusions verbose)]
          (let [use-path (conj parents lib)
                {manifest-type :deps/manifest :as manifest-info} (ext/manifest-type lib use-coord config)
                use-coord (merge use-coord manifest-info)
                children (canonicalize-deps (ext/coord-deps lib use-coord manifest-type config) config)
                child-paths (map #(conj use-path %) children)
                vmap' (add-coord version-map lib coord-id use-coord parents action config verbose)]
            (if vmap'
              (recur
                (into q' child-paths)
                vmap'
                (if-let [excl (:exclusions use-coord)]
                  (add-exclusion exclusions use-path excl)
                  exclusions))
              (recur q' version-map exclusions)))
          (recur q' version-map exclusions)))
      (do
        (when verbose (println) (println "Version map:") (pprint version-map))
        version-map))))

(defn- lib-paths
  [version-map config]
  (reduce
    (fn [ret [lib {:keys [select versions paths]}]]
      (let [coord (get versions select)
            paths (->> (get paths select) (map last) (remove nil?) vec)
            src-paths (ext/coord-paths lib coord (:deps/manifest coord) config)]
        (assoc ret
          lib
          (cond-> (assoc coord :paths src-paths)
            (seq paths) (assoc :dependents paths)))))
    {} version-map))

(defn resolve-deps
  "Takes a deps configuration map and resolves the transitive dependency graph
  from the initial set of deps. args-map is a map with several keys (all
  optional) that can modify the results of the transitive expansion:

    :extra-deps - a map from lib to coord of extra deps to include
    :override-deps - a map from lib to coord of coord to use instead of those in the graph
    :default-deps - a map from lib to coord of deps to use if no coord specified

  Returns a lib map (map of lib to coordinate chosen)."
  [{:keys [deps] :as deps-map} args-map]
  (let [{:keys [extra-deps default-deps override-deps verbose]} args-map
        deps (merge (:deps deps-map) extra-deps)]
    (when verbose
      (println "Initial deps to expand:")
      (pprint deps))
    (-> deps
      (canonicalize-deps deps-map)
      (expand-deps default-deps override-deps deps-map verbose)
      (lib-paths deps-map))))

(defn- make-tree
  [lib-map]
  (let [{roots false, nonroots true} (group-by #(-> % val :dependents boolean) lib-map)]
    (loop [q (into (PersistentQueue/EMPTY) roots)
           remaining nonroots
           tree {}]
      (let [[lib coord :as node] (peek q)
            q' (pop q)]
        (if node
          (let [{children true, not-used false} (group-by #(-> % val :dependents set (contains? lib)) remaining)]
            (recur (into q' children) not-used (assoc tree lib (assoc coord :children (map key children)))))
          tree)))))

(defn print-tree
  "Print lib-map tree to the console"
  [lib-map]
  (let [tree (make-tree lib-map)]
    (letfn [(print-node [lib indent]
              (let [{:keys [children] :as coord} (get tree lib)]
                (println (str indent (ext/coord-summary lib coord)))
                (doseq [child-lib children]
                  (print-node child-lib (str indent "  ")))))]
      (doseq [[lib coord] tree :when (-> coord :dependents nil?)]
        (print-node lib "")))))

(defn make-classpath
  "Takes a lib map, and a set of explicit paths. Extracts the paths for each chosen
  lib coordinate, and assembles a classpath string using the system path separator.
  The classpath-args is a map with keys that can be used to modify the classpath
  building operation:

    :classpath-overrides - a map of lib to path, where path is used instead of the coord's paths
    :extra-paths - extra classpath paths to add to the classpath

  Returns the classpath as a string."
  [lib-map paths {:keys [classpath-overrides extra-paths] :as classpath-args}]
  (let [libs (merge-with (fn [coord path] (assoc coord :paths [path])) lib-map classpath-overrides)
        lib-paths (mapcat :paths (vals libs))]
    (str/join File/pathSeparator (concat extra-paths paths lib-paths))))

(comment
  (require '[clojure.tools.deps.alpha.util.maven :as mvn])

  (expand-deps {'org.clojure/clojure {:mvn/version "1.9.0"}}
    nil nil {:mvn/repos mvn/standard-repos} true)

  (expand-deps {'org.apache.xmlgraphics/batik-transcoder {:mvn/version "1.7"}}
               nil nil {:mvn/repos mvn/standard-repos} true)

  (expand-deps {'org.clojure/clojure {:mvn/version "1.9.0"}
                'org.clojure/core.memoize {:mvn/version "0.5.8"}}
    nil nil {:mvn/repos mvn/standard-repos} true)

  (expand-deps {'org.clojure/clojure {:mvn/version "1.9.0"}
                'org.clojure/clojurescript {:mvn/version "1.9.946"}
                'reagent/reagent {:mvn/version "0.6.0"}}
    nil nil {:mvn/repos mvn/standard-repos} true)

  (expand-deps {'org.clojure/core.async {:mvn/version "0.3.426"}
                'reagent/reagent {:mvn/version "0.6.0"}}
    nil nil {:mvn/repos mvn/standard-repos} true)

  (expand-deps {'org.clojure/tools.deps.alpha {:mvn/version "0.4.277"}}
    nil nil {:mvn/repos mvn/standard-repos} true)

  (print-tree
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

  ;; override-deps
  (make-classpath
    (resolve-deps
      {:deps {'org.clojure/core.memoize {:mvn/version "0.5.8"}}
       :mvn/repos mvn/standard-repos}
      {:override-deps {'org.clojure/clojure {:mvn/version "1.3.0"}}
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

  (resolve-deps
    {:deps '{org.clojure/clojure {:mvn/version "1.9.0"}
             org.clojure/clojurescript {:mvn/version "1.9.946"}
             reagent {:mvn/version "0.6.0"}}
     :mvn/repos mvn/standard-repos}
    {:verbose true})

  ;; err case
  (resolve-deps {:deps {'bogus {:mvn/version "1.2.3"}}
                 :mvn/repos mvn/standard-repos} nil)

  (resolve-deps {:deps {'bogus "1.2.3"}
                 :mvn/repos mvn/standard-repos} nil)

  (require
    '[clojure.tools.deps.alpha.extensions.git]
    '[clojure.tools.deps.alpha.extensions.deps])

  (resolve-deps
    {:deps {'foo {:git/url "https://github.com/clojure/core.async.git"
                  :sha "ecea2539a724a415b15e50f12815b4ab115cfd35"}}}
    nil)

  )
