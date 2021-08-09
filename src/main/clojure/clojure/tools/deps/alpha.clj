;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha
  (:require
    [clojure.java.io :as jio]
    [clojure.pprint :refer [pprint]]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.util.concurrent :as concurrent]
    [clojure.tools.deps.alpha.util.dir :as dir]
    [clojure.tools.deps.alpha.util.io :as io]
    [clojure.tools.deps.alpha.util.session :as session]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.walk :as walk])
  (:import
    [clojure.lang EdnReader$ReaderException]
    [clojure.lang PersistentQueue]
    [java.io File InputStreamReader BufferedReader]
    [java.lang ProcessBuilder ProcessBuilder$Redirect]
    [java.util List]))

;(set! *warn-on-reflection* true)

;;;; deps.edn reading

(defn- io-err
  ^Throwable [fmt ^File f]
  (let [path (.getAbsolutePath f)]
    (ex-info (format fmt path) {:path path})))

(defn- slurp-edn-map
  "Read the file specified by the path-segments, slurp it, and read it as edn."
  [^File f]
  (let [val (try (io/slurp-edn f)
                 (catch EdnReader$ReaderException e (throw (io-err (str (.getMessage e) " (%s)") f)))
                 (catch RuntimeException t
                   (if (str/starts-with? (.getMessage t) "EOF while reading")
                     (throw (io-err "Error reading edn, delimiter unmatched (%s)" f))
                     (throw (io-err (str "Error reading edn. " (.getMessage t) " (%s)") f)))))]
    (if (map? val)
      val
      (throw (io-err "Expected edn map in: %s" f)))))

;; all this canonicalization is deprecated and will eventually be removed

(defn- canonicalize-sym
  ([s]
   (canonicalize-sym s nil))
  ([s file-name]
   (if (simple-symbol? s)
     (let [cs (as-> (name s) n (symbol n n))]
       (io/printerrln "DEPRECATED: Libs must be qualified, change" s "=>" cs
         (if file-name (str "(" file-name ")") ""))
       cs)
     s)))

(defn- canonicalize-exclusions
  [{:keys [exclusions] :as coord} file-name]
  (if (seq (filter simple-symbol? exclusions))
    (assoc coord :exclusions (mapv #(canonicalize-sym % file-name) exclusions))
    coord))

(defn- canonicalize-dep-map
  [deps-map file-name]
  (when deps-map
    (reduce-kv (fn [acc lib coord]
                 (let [new-lib (if (simple-symbol? lib) (canonicalize-sym lib file-name) lib)
                       new-coord (canonicalize-exclusions coord file-name)]
                   (assoc acc new-lib new-coord)))
      {} deps-map)))

(defn- canonicalize-all-syms
  ([deps-edn]
   (canonicalize-all-syms deps-edn nil))
  ([deps-edn file-name]
   (walk/postwalk
     (fn [x]
       (if (map? x)
         (reduce (fn [xr k]
                   (if-let [xm (get xr k)]
                     (assoc xr k (canonicalize-dep-map xm file-name))
                     xr))
           x #{:deps :default-deps :override-deps :extra-deps :classpath-overrides})
         x))
     deps-edn)))

(defn slurp-deps
  "Read a single deps.edn file from disk and canonicalize symbols,
  return a deps map. If the file doesn't exist, returns nil."
  [^File dep-file]
  (when (.exists dep-file)
    (-> dep-file slurp-edn-map (canonicalize-all-syms (.getPath dep-file)))))

(defn root-deps
  "Read the root deps.edn resource from the classpath at the path
  clojure/tools/deps/deps.edn"
  []
  (let [url (jio/resource "clojure/tools/deps/deps.edn")]
    (io/read-edn (BufferedReader. (InputStreamReader. (.openStream url))))))

(defn user-deps-path
  "Use the same logic as clj to calculate the location of the user deps.edn.
  Note that it's possible no file may exist at this location."
  []
  (let [config-env (System/getenv "CLJ_CONFIG")
        xdg-env (System/getenv "XDG_CONFIG_HOME")
        home (System/getProperty "user.home")
        config-dir (cond config-env config-env
                         xdg-env (str xdg-env File/separator "clojure")
                         :else (str home File/separator ".clojure"))]
    (str config-dir File/separator "deps.edn")))

(defn find-edn-maps
  "Finds and returns standard deps edn maps in a map with keys
    :root-edn, :user-edn, :project-edn
  If no project-edn is supplied, use the deps.edn in current directory"
  ([]
   (find-edn-maps nil))
  ([project-edn-file]
   (let [user-loc (jio/file (user-deps-path))
         project-loc (jio/file (if project-edn-file project-edn-file (str dir/*the-dir* File/separator "deps.edn")))]
     (cond-> {:root-edn (root-deps)}
       (.exists user-loc) (assoc :user-edn (slurp-deps user-loc))
       (.exists project-loc) (assoc :project-edn (slurp-deps project-loc))))))

(defn- merge-or-replace
  "If maps, merge, otherwise replace"
  [& vals]
  (when (some identity vals)
    (reduce (fn [ret val]
              (if (and (map? ret) (map? val))
                (merge ret val)
                (or val ret)))
      nil vals)))

(defn merge-edns
  "Merge multiple deps edn maps from left to right into a single deps edn map."
  [deps-edn-maps]
  (apply merge-with merge-or-replace (remove nil? deps-edn-maps)))

;;;; Aliases

;; per-key binary merge-with rules

(def ^:private last-wins (comp last #(remove nil? %) vector))
(def ^:private append (comp vec concat))
(def ^:private append-unique (comp vec distinct concat))

(def ^:private merge-alias-rules
  {:deps merge ;; FUTURE: remove
   :replace-deps merge ;; formerly :deps
   :extra-deps merge
   :override-deps merge
   :default-deps merge
   :classpath-overrides merge
   :paths append-unique ;; FUTURE: remove
   :replace-paths append-unique ;; formerly :paths
   :extra-paths append-unique
   :jvm-opts append
   :main-opts last-wins
   :exec-fn last-wins
   :exec-args merge-or-replace
   :ns-aliases merge
   :ns-default last-wins})

(defn- choose-rule [alias-key val]
  (or (merge-alias-rules alias-key)
    (if (map? val)
      merge
      (fn [_v1 v2] v2))))

(defn- merge-alias-maps
  "Like merge-with, but using custom per-alias-key merge function"
  [& ms]
  (reduce
    #(reduce
       (fn [m [k v]] (update m k (choose-rule k v) v))
       %1 %2)
    {} ms))

(defn combine-aliases
  "Find, read, and combine alias maps identified by alias keywords from
  a deps edn map into a single args map."
  [edn-map alias-kws]
  (->> alias-kws
    (map #(get-in edn-map [:aliases %]))
    (apply merge-alias-maps)))

(defn lib-location
  "Find the file path location of where a lib/coord would be located if procured
  without actually doing the procuring!"
  [lib coord deps-config]
  (ext/lib-location lib coord deps-config))

;;;; expand-deps

;; exclusions tree

(defn- excluded?
  [exclusions path lib]
  (let [lib-name (first (str/split (name lib) #"\$"))
        base-lib (symbol (namespace lib) lib-name)]
    (loop [search path]
      (when (seq search)
        (if (get-in exclusions [search base-lib])
          true
          (recur (pop search)))))))

(defn- update-excl
  "Update exclusions and cut based on whether this is a new lib/version,
  a new instance of an existing lib/version, or not including."
  [lib use-coord coord-id use-path include reason exclusions cut]
  (let [coord-excl (when-let [e (:exclusions use-coord)] (set e))]
    (cond
      ;; if adding new lib/version, include all non-excluded children
      include
      (if (nil? coord-excl)
        {:exclusions' exclusions, :cut' cut, :child-pred (constantly true)}
        {:exclusions' (assoc exclusions use-path coord-excl)
         :cut' (assoc cut [lib coord-id] coord-excl)
         :child-pred (fn [lib] (not (contains? coord-excl lib)))})

      ;; if seeing same lib/ver again, narrow exclusions to intersection of prior and new.
      ;; only include new unexcluded children (old excl set minus new excl set)
      ;; as others were already enqueued when first added
      (= reason :same-version)
      (let [exclusions' (if (seq coord-excl) (assoc exclusions use-path coord-excl) exclusions)
            cut-coord (get cut [lib coord-id]) ;; previously cut from this lib, so were not enqueued
            new-cut (set/intersection coord-excl cut-coord)
            enq-only (set/difference cut-coord new-cut)]
        {:exclusions' exclusions'
         :cut' (assoc cut [lib coord-id] new-cut)
         :child-pred (set enq-only)})

      :else ;; otherwise, no change
      {:exclusions' exclusions, :cut' cut})))

;; version map

;; {lib {:versions {coord-id coord}     ;; all version coords
;;       :paths    {coord-id #{paths}}  ;; paths to coord-ids
;;       :select   coord-id             ;; current selection
;;       :top      true}                ;; if selection is top dep

(defn- add-version
  "Add a new version of a lib to the version map"
  [vmap lib coord path coord-id]
  (-> (or vmap {})
    (assoc-in [lib :versions coord-id] coord)
    (update-in [lib :paths]
      (fn [coord-paths]
        (merge-with into {coord-id #{path}} coord-paths)))))

(defn- select-version
  "Mark a particular coord as selected in version map"
  [vmap lib coord-id top?]
  (update-in vmap [lib] merge (cond-> {:select coord-id}
                                top? (assoc :top true))))

(defn- selected-version
  "Get currently selected version of lib"
  [vmap lib]
  (get-in vmap [lib :select]))

(defn- selected-coord
  "Get currently selected coord of lib"
  [vmap lib]
  (get-in vmap [lib :versions (selected-version vmap lib)]))

(defn- selected-paths
  "Get paths to selected coord of lib"
  [vmap lib]
  (get-in vmap [lib :paths (selected-version vmap lib)]))

(defn- parent-missing?
  "Is any part of the parent path missing from the selected lib/versions?
  This can happen if a newer version was found, orphaning previously selected children."
  [vmap parent-path]
  (when (seq parent-path)
    (loop [path parent-path]
      (if (seq path)
        (let [lib (last path)
              check-path (vec (butlast path))
              {:keys [paths select]} (get vmap lib)]
          (if (contains? (get paths select) check-path)
            (recur check-path)
            true))
        false))))

(defn- deselect-orphans
  "For the given paths, deselect any libs whose only selected version paths are in omitted-paths"
  [vmap omitted-paths]
  (reduce-kv
    (fn [ret lib {:keys [select paths]}]
      (let [lib-paths (get paths select)]
        ;; if every selected path for this lib has omitted paths as prefixes, deselect
        (if (every? (fn [p] (some #(= % (take (count %) p)) omitted-paths)) lib-paths)
          (update-in ret [lib] dissoc :select)
          ret)))
    vmap
    vmap))

(defn- dominates?
  "Is new-coord newer than old-coord?"
  [lib new-coord old-coord config]
  (pos? (ext/compare-versions lib new-coord old-coord config)))

(defn- include-coord?
  "This is the key decision-making function when considering a lib/coord node in
  the traversal graph. It returns :include (whether to include this lib/coord), a
  :reason why it was included or not, and an updated :vmap (may have new version added
  and/or new selected version for a lib)"
  [vmap lib coord coord-id path exclusions config]
  (cond
    ;; lib is a top dep and this is it => select
    (empty? path)
    {:include true, :reason :new-top-dep,
     :vmap (-> vmap
             (add-version lib coord path coord-id)
             (select-version lib coord-id true))}

    ;; lib is excluded in this path => omit
    (excluded? exclusions path lib)
    {:include false, :reason :excluded, :vmap vmap}

    ;; lib is a top dep and this isn't it => omit
    (get-in vmap [lib :top])
    {:include false, :reason :use-top, :vmap vmap}

    ;; lib's parent path is not included => omit
    (parent-missing? vmap path)
    {:include false, :reason :parent-omitted, :vmap vmap}

    ;; new lib or no selection => select
    (not (selected-version vmap lib))
    {:include true, :reason :new-dep,
     :vmap (-> vmap
             (add-version lib coord path coord-id)
             (select-version lib coord-id false))}

    ;; existing lib, same version => omit (but update vmap, may need to enqueue newly unexcluded children)
    (= coord-id (selected-version vmap lib))
    {:include false, :reason :same-version, :vmap (add-version vmap lib coord path coord-id)}

    ;; existing lib, newer version => select
    (dominates? lib coord (selected-coord vmap lib) config)
    {:include true, :reason :newer-version,
     :vmap (-> vmap
             (add-version lib coord path coord-id)
             (deselect-orphans (set (map #(conj % lib) (selected-paths vmap lib))))
             (select-version lib coord-id false))}

    ;; existing lib, older version => omit
    :else
    {:include false, :reason :older-version, :vmap vmap}))

(defn- canonicalize-deps
  [deps config]
  (reduce
    (fn [m [lib coord]] (conj m (ext/canonicalize lib coord config)))
    [] deps))

(defn- trace+
  "Add an entry to the trace if needed"
  [trace? trace parents lib coord use-coord coord-id override-coord include reason]
  (when trace?
    (let [entry (cond-> {:path parents, :lib lib, :coord use-coord, :orig-coord coord, :coord-id coord-id
                         :include include, :reason reason}
                  override-coord (assoc :override-coord override-coord))]
      (conj trace entry))))

(defn- next-path
  [pendq q on-error]
  (let [[fchild & rchildren] pendq]
    (if fchild
      {:path fchild, :pendq rchildren, :q' q}
      (let [next-q (peek q)
            q' (pop q)]
        (if (map? next-q)
          (let [{:keys [pend-children ppath child-pred]} next-q
                result @pend-children]
            (when (instance? Throwable result)
              (on-error result))
            (next-path (->> result (filter (fn [[lib _coord]] (child-pred lib))) (map #(conj ppath %))) q' on-error))
          {:path next-q, :q' q'})))))

(defn- expand-deps
  "Dep tree expansion, returns version map"
  [deps default-deps override-deps config executor trace?]
  (letfn [(err-handler [throwable]
            (do
              (concurrent/shutdown-on-error executor)
              (throw ^Throwable throwable)))
          (children-task [lib use-coord use-path child-pred]
            {:pend-children
             (let [{:deps/keys [manifest root]} use-coord]
               (dir/with-dir (if root (jio/file root) dir/*the-dir*)
                 (concurrent/submit-task executor
                   #(try
                      (canonicalize-deps (ext/coord-deps lib use-coord manifest config) config)
                      (catch Throwable t t)))))
             :ppath use-path
             :child-pred child-pred})]
    (loop [pendq nil ;; a resolved child-lookup thunk to look at first
           q (into (PersistentQueue/EMPTY) (map vector deps)) ;; queue of nodes or child-lookups
           version-map nil ;; track all seen versions of libs and which version is selected
           exclusions nil ;; tracks exclusions marked in the tree
           cut nil ;; tracks cuts made of child nodes based on exclusions
           trace []] ;; trace expansion
      (let [{:keys [path pendq q']} (next-path pendq q err-handler)]
        (if path
          (let [[lib coord] (peek path)
                parents (pop path)
                use-path (conj parents lib)
                override-coord (get override-deps lib)
                choose-coord (cond override-coord override-coord
                                   coord coord
                                   :else (get default-deps lib))
                use-coord (merge choose-coord (ext/manifest-type lib choose-coord config))
                coord-id (ext/dep-id lib use-coord config)
                {:keys [include reason vmap]} (include-coord? version-map lib use-coord coord-id parents exclusions config)
                ;_ (println "loop" lib coord-id "include=" include "reason=" reason)
                {:keys [exclusions' cut' child-pred]} (update-excl lib use-coord coord-id use-path include reason exclusions cut)
                new-q (if child-pred (conj q' (children-task lib use-coord use-path child-pred)) q')]
            (recur pendq new-q vmap exclusions' cut'
              (trace+ trace? trace parents lib coord use-coord coord-id override-coord include reason)))
          (cond-> version-map trace? (with-meta {:trace {:log trace, :vmap version-map, :exclusions exclusions}})))))))

(defn- cut-orphans
  "Remove any selected lib that does not have a selected parent path"
  [version-map]
  (reduce-kv
    (fn [vmap lib {:keys [select]}]
      (if (nil? select)
        (dissoc vmap lib)
        vmap))
    version-map version-map))

(defn- lib-paths
  "Convert version map to lib map"
  [version-map]
  (reduce-kv
    (fn [ret lib {:keys [select versions paths]}]
      (let [coord (get versions select)
            parent-paths (get paths select)
            parents (->> parent-paths (map last) (remove nil?) vec)]
        (assoc ret lib (cond-> coord
                         (seq parents) (assoc :dependents parents)
                         (seq parent-paths) (assoc :parents parent-paths)))))
    {} version-map))

(defn- download-libs
  [executor lib-map config]
  (let [lib-futs (reduce-kv
                   (fn [fs lib coord]
                     (let [fut (concurrent/submit-task
                                 executor
                                 #(try
                                    (ext/coord-paths lib coord (:deps/manifest coord) config)
                                    (catch Throwable t t)))]
                       (assoc fs lib fut)))
                   {} lib-map)]
    (reduce-kv (fn [lm lib fut]
                 (let [result @fut]
                   (if (instance? Throwable result)
                     (do
                       (concurrent/shutdown-on-error executor)
                       (throw ^Throwable result))
                     (assoc-in lm [lib :paths] result))))
      lib-map lib-futs)))

(defn resolve-deps
  "Takes a deps configuration map and resolves the transitive dependency graph
  from the initial set of deps. args-map is a map with several keys (all
  optional) that can modify the results of the transitive expansion:

    :extra-deps - a map from lib to coord of deps to add to the main deps
    :override-deps - a map from lib to coord of coord to use instead of those in the graph
    :default-deps - a map from lib to coord of deps to use if no coord specified
    :trace - boolean. If true, the returned lib map will have metadata with :trace log
    :threads - long. If provided, sets the number of concurrent download threads

  Returns a lib map (map of lib to coordinate chosen)."
  {:arglists '([deps-map args-map])}
  ([deps-map args-map]
   (let [{:keys [extra-deps default-deps override-deps threads trace]} args-map
         n (or threads concurrent/processors)
         executor (concurrent/new-executor n)
         deps (merge (:deps deps-map) extra-deps)
         version-map (-> deps
                       (canonicalize-deps deps-map)
                       (expand-deps default-deps override-deps deps-map executor trace)
                       cut-orphans)
         lib-map (lib-paths version-map)
         lib-map' (download-libs executor lib-map deps-map)]
     (with-meta lib-map' (meta version-map))))
  ;; deprecated arity, retained for backwards compatibility
  ([deps-map args-map settings]
   (resolve-deps deps-map (merge args-map settings))))

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

(defn- chase-key
  "Given an aliases set and a keyword k, return a flattened vector of path
  entries for that k, resolving recursively if needed, or nil."
  [aliases k]
  (let [path-coll (get aliases k)]
    (when (seq path-coll)
      (into [] (mapcat #(if (string? %) [[% {:path-key k}]] (chase-key aliases %))) path-coll))))

(defn- flatten-paths
  [{:keys [paths aliases] :as deps-edn-map} {:keys [extra-paths] :as classpath-args}]
  (let [aliases' (assoc aliases :paths paths :extra-paths extra-paths)]
    (into [] (mapcat #(chase-key aliases' %)) (remove nil? [:extra-paths :paths]))))

(defn- validate-paths
  [paths]
  (doto paths
    (->> (map first)
      (run! #(when (not (dir/sub-path? (jio/file %)))
               (io/printerrln "WARNING: Use of :paths external to the project has been deprecated, please remove:" %))))))

(defn- tree-paths
  "Given a lib map, return a vector of all vector paths to included libs in the tree.
  Libs are often included at multiple paths."
  [lib-map]
  (loop [paths []
         [lib & libs] (keys lib-map)]
    (if lib
      (let [parent-paths (:parents (get lib-map lib))]
        (recur
          (reduce (fn [paths parent-path] (conj paths (conj parent-path lib))) paths parent-paths)
          libs))
      paths)))

(defn- sort-paths
  [lib-paths]
  "Given a vector of lib paths, sort in canonical order -
  top of tree to bottom, alpha sort at same level"
  (->> lib-paths sort (sort-by count) vec))

(defn- flatten-libs
  [lib-map {:keys [classpath-overrides] :as classpath-args}]
  (let [override-libs (if classpath-overrides
                        (reduce-kv
                          (fn [lm lib path]
                            (if (contains? lm lib)
                              (if (str/blank? path)
                                ;; classpath-override removes lib
                                (dissoc lm lib)
                                ;; override path for lib
                                (assoc-in lm [lib :paths] [path]))
                              lm))
                          lib-map
                          classpath-overrides)
                        lib-map)
        lib-order (->> override-libs tree-paths sort-paths (map peek) distinct)
        lib-paths (mapcat
                    #(map vector (get-in override-libs [% :paths]) (repeat {:lib-name %}))
                    lib-order)]
    lib-paths))

(defn make-classpath-map
  "Takes a merged deps edn map and a lib map. Extracts the paths for each chosen
  lib coordinate, and assembles a classpath map. The classpath-args is a map with
  keys that can be used to modify the classpath building operation:
    :extra-paths - extra classpath paths to add to the classpath
    :classpath-overrides - a map of lib to path, where path is used instead of the coord's paths

  Returns a map:
    :classpath map of path entry (string) to a map describing where its from,  either a :lib-name or :path-key entry.
    :classpath-roots coll of the classpath keys in classpath order"
  [deps-edn-map lib-map classpath-args]
  (let [flat-paths (->> classpath-args (flatten-paths deps-edn-map) validate-paths)
        flat-libs (flatten-libs lib-map classpath-args)
        all-paths (concat flat-paths flat-libs)
        cp (mapv first all-paths)
        cp-map (reduce (fn [m [path-str src]] (assoc m path-str src)) {} all-paths)]
    {:classpath-roots cp
     :classpath cp-map}))

(defn join-classpath
  "Takes a coll of string classpath roots and creates a platform sensitive classpath"
  [roots]
  (str/join File/pathSeparator roots))

(defn make-classpath
  "Takes a lib map, and a set of explicit paths. Extracts the paths for each chosen
  lib coordinate, and assembles a classpath string using the system path separator.
  The classpath-args is a map with keys that can be used to modify the classpath
  building operation:

    :extra-paths - extra classpath paths to add to the classpath
    :classpath-overrides - a map of lib to path, where path is used instead of the coord's paths

  Returns the classpath as a string."
  {:deprecated "0.9.745"}
  [lib-map paths classpath-args]
  (-> (make-classpath-map {:paths paths} lib-map classpath-args) :classpath-roots join-classpath))

(defn tool
  "Transform project edn for tool by applying tool args (keys = :paths, :deps) and
  returning an updated project edn."
  [project-edn tool-args]
  (let [{:keys [replace-deps replace-paths deps paths]} tool-args]
    (cond-> project-edn
      (or deps replace-deps) (merge {:deps (merge deps replace-deps)})
      (or paths replace-paths) (merge {:paths (vec (concat paths replace-paths))}))))

(defn- qualify-fn
  "Compute function symbol based on fn, ns-aliases, and ns-default"
  [fsym {:keys [ns-aliases ns-default] :as x}]
  (when (and fsym (not (symbol? fsym)))
    (throw (ex-info (format "Expected function symbol: %s" fsym) {})))

  (when fsym
    (if (qualified-ident? fsym)
      (let [nsym (get ns-aliases (symbol (namespace fsym)))]
        (if nsym
          (symbol (str nsym) (name fsym))
          fsym))
      (if ns-default
        (symbol (str ns-default) (str fsym))
        (throw (ex-info (format "Unqualified function can't be resolved: %s" fsym) {}))))))

(defn- exec-prep!
  "Exec the prep command in the command-args coll. Redirect stdout/stderr to this process,
  wait for the process to complete, and return the exit code"
  [^File dir classpath f]
  ;; java -cp <classpath> clojure.main -e '(do ((requiring-resolve f) nil) nil)'
  (let [command-args ["java" "-cp" classpath "clojure.main" "-e" (str "(do ((requiring-resolve '" f ") nil) nil)")]
        ;;_ (apply println (map #(if (str/includes? % " ") (str "\"" % "\"") %) command-args))
        proc-builder (doto (ProcessBuilder. ^List command-args)
                       (.directory dir)
                       (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                       (.redirectError ProcessBuilder$Redirect/INHERIT))
        proc (.start proc-builder)]
    (.waitFor proc)))

(declare create-basis)

(defn prep-libs!
  "Takes a lib map and looks for unprepped libs, optionally prepping them.

  Options:
    :action - what to do when an unprepped lib is found, one of:
                :prep - if unprepped, prep
                :force - prep regardless of whether already prepped
                :error (default) - don't prep, error
    :log -  print to console based on log level (default, no logging):
              :info  - print only when prepping
              :debug - :info + print for each lib considered"
  [lib-map {:keys [action log]} config]
  (let [unprepped
        (reduce-kv
          (fn [ret lib {:deps/keys [root manifest] :as coord}]
            (if-let [{f :fn, :keys [alias ensure]} (ext/prep-command lib coord manifest config)]
              (let [ensure-dir (when ensure (jio/file root ensure))
                    unprepped (and ensure (not (.exists ^File ensure-dir)))]
                (when (#{:debug} log) (println lib "-" (if unprepped "unprepped" "prepped")))
                (if (or (= action :force) (and unprepped (= action :prep)))
                  (do
                    (when (#{:info :debug} log) (println "Prepping" lib "in" root))
                    (let [root-dir (jio/file root)
                          basis (create-basis
                                  {:project (.getAbsolutePath (jio/file root "deps.edn"))
                                   :extra {:aliases {:deps/TOOL {:replace-deps {} :replace-paths ["."]}}}
                                   :aliases [:deps/TOOL alias]})
                          cp (join-classpath (:classpath-roots basis))
                          qual-f (qualify-fn f (get-in basis [:aliases alias]))
                          exit (exec-prep! root-dir cp qual-f)]
                      (when (not (zero? exit))
                        (throw (ex-info (format "Error building %s" lib) {:lib lib :exit exit})))
                      ret))
                  (if unprepped (conj (or ret []) lib) ret)))
              (do
                (when (#{:debug} log) (println lib "- no prep"))
                ret)))
          nil lib-map)]
    (when unprepped
      (throw (ex-info (format "The following libs must be prepared before use: %s" unprepped)
               {:unprepped unprepped})))))

(defn calc-basis
  "Calculates and returns the runtime basis from a master deps edn map, modifying
   resolve-deps and make-classpath args as needed.

    master-edn - a master deps edn map
    args - an optional map of arguments to constituent steps, keys:
      :resolve-args - map of args to resolve-deps, with possible keys:
        :extra-deps
        :override-deps
        :default-deps
        :threads - number of threads to use during deps resolution
        :trace - flag to record a trace log
      :classpath-args - map of args to make-classpath-map, with possible keys:
        :extra-paths
        :classpath-overrides

  Returns the runtime basis, which is the initial deps edn map plus these keys:
    :resolve-args - the resolve args passed in, if any
    :classpath-args - the classpath args passed in, if any
    :libs - lib map, per resolve-deps
    :classpath - classpath map per make-classpath-map
    :classpath-roots - vector of paths in classpath order"
  ([master-edn]
   (calc-basis master-edn nil))
  ([master-edn {:keys [resolve-args classpath-args] :as argmaps}]
   (session/with-session
     (let [libs (resolve-deps master-edn resolve-args)
           cp (make-classpath-map master-edn libs classpath-args)]
       (merge master-edn {:libs libs} cp argmaps)))))

;(defn runtime-basis
;  "Load the runtime execution basis context and return it."
;  []
;  (when-let [f (jio/file (System/getProperty "clojure.basis"))]
;    (if (and f (.exists f))
;      {:basis (slurp-deps f)}
;      (throw (IllegalArgumentException. "No basis declared in clojure.basis system property")))))

(defn- choose-deps
  [requested standard-fn]
  (cond
    (= :standard requested) (standard-fn)
    (string? requested) (-> requested jio/file dir/canonicalize slurp-deps)
    (or (nil? requested) (map? requested)) requested
    :else (throw (ex-info (format "Unexpected dep source: %s" (pr-str requested))
                   {:requested requested}))))

(defn create-basis
  "Create a basis from a set of deps sources and a set of aliases. By default, use
   root, user, and project deps and no argmaps (essentially the same classpath you get by
   default from the Clojure CLI).

   Each dep source value can be :standard, a string path, a deps edn map, or nil.
   Sources are merged in the order - :root, :user, :project, :extra.

   Aliases refer to argmaps in the merged deps that will be supplied to the basis
   subprocesses (tool, resolve-deps, make-classpath-map).

   The following subprocess argmap args can be provided:
     Key                  Subproc             Description
     :replace-deps        tool                Replace project deps
     :replace-paths       tool                Replace project paths
     :extra-deps          resolve-deps        Add additional deps
     :override-deps       resolve-deps        Override coord of dep
     :default-deps        resolve-deps        Provide coord if missing
     :extra-paths         make-classpath-map  Add additional paths
     :classpath-overrides make-classpath-map  Replace lib path in cp

   Options:
     :root    - dep source, default = :standard
     :user    - dep source, default = :standard
     :project - dep source, default = :standard (\"./deps.edn\")
     :extra   - dep source, default = nil
     :aliases - coll of aliases of argmaps  to apply to subprocesses

   Returns a runtime basis, which is the initial merged deps edn map plus these keys:
    :resolve-args - the resolve args passed in, if any
    :classpath-args - the classpath args passed in, if any
    :libs - lib map, per resolve-deps
    :classpath - classpath map per make-classpath-map
    :classpath-roots - vector of paths in classpath order"
  [{:keys [root user project extra aliases] :as params
    :or {root :standard, user :standard, project :standard}}]
  (let [root-edn (choose-deps root #(root-deps))
        user-edn (choose-deps user #(-> (user-deps-path) jio/file dir/canonicalize slurp-deps))
        project-edn (choose-deps project #(-> "deps.edn" jio/file dir/canonicalize slurp-deps))
        extra-edn (choose-deps extra (constantly nil))
        edn-maps [root-edn user-edn project-edn extra-edn]

        alias-data (->> edn-maps
                     (map :aliases)
                     (remove nil?)
                     (apply merge-with merge))

        argmap-data (->> aliases
                      (remove nil?)
                      (map #(get alias-data %)))
        argmap (apply merge-alias-maps argmap-data)
        project-tooled-edn (tool project-edn argmap)
        master-edn (merge-edns [root-edn user-edn project-tooled-edn extra-edn])]
    (calc-basis master-edn {:resolve-args argmap :classpath-args argmap})))

;; Load extensions
(load "/clojure/tools/deps/alpha/extensions/maven")
(load "/clojure/tools/deps/alpha/extensions/local")
(load "/clojure/tools/deps/alpha/extensions/git")
(load "/clojure/tools/deps/alpha/extensions/deps")
(load "/clojure/tools/deps/alpha/extensions/pom")

(comment
  (require '[clojure.tools.deps.alpha.util.maven :as mvn])

  (def ex-svc (concurrent/new-executor 2))

  (calc-basis
    {:mvn/repos mvn/standard-repos
     :paths ["src" "test"]
     :deps {'org.clojure/clojure {:mvn/version "1.9.0"}
            'org.clojure/core.memoize {:mvn/version "0.5.8"}}
     :aliases {:x {:extra-paths ["a" "b"]}}}
    {:classpath-args {:extra-paths ["a" "b"]}})

  (expand-deps {'org.clojure/clojure {:mvn/version "1.9.0"}}
    nil nil {:mvn/repos mvn/standard-repos} ex-svc true)

  (expand-deps {'org.apache.xmlgraphics/batik-transcoder {:mvn/version "1.7"}}
    nil nil {:mvn/repos mvn/standard-repos} ex-svc true)

  (expand-deps {'org.clojure/clojure {:mvn/version "1.9.0"}
                'org.clojure/core.memoize {:mvn/version "0.5.8"}}
    nil nil {:mvn/repos mvn/standard-repos} ex-svc true)

  (expand-deps {'org.clojure/clojure {:mvn/version "1.9.0"}
                'org.clojure/clojurescript {:mvn/version "1.9.946"}
                'reagent/reagent {:mvn/version "0.6.0"}}
    nil nil {:mvn/repos mvn/standard-repos} ex-svc true)

  (expand-deps {'org.clojure/core.async {:mvn/version "0.3.426"}
                'reagent/reagent {:mvn/version "0.6.0"}}
    nil nil {:mvn/repos mvn/standard-repos} ex-svc true)

  (expand-deps {'org.clojure/tools.deps.alpha {:mvn/version "0.4.277"}}
    nil nil {:mvn/repos mvn/standard-repos} ex-svc true)

  (expand-deps '{org.clojure/clojure              {:mvn/version "1.10.3"}
                 com.datomic/ion                  {:mvn/version "0.9.50"},
                 software.amazon.awscdk/codebuild {:mvn/version "1.115.0"}}
    nil nil {:mvn/repos (merge mvn/standard-repos {"datomic-cloud" {:url "s3://datomic-releases-1fc2183a/maven/releases"}})}
    ex-svc true)

  (print-tree
    (resolve-deps {:deps {'org.clojure/clojure {:mvn/version "1.8.0"}
                          'org.clojure/core.memoize {:mvn/version "0.5.8"}}
                   :mvn/repos mvn/standard-repos} nil))

  (make-classpath-map
    {:paths ["src"]}
    (resolve-deps {:deps {'org.clojure/clojure {:mvn/version "1.9.0-alpha17"}
                          'org.clojure/core.memoize {:mvn/version "0.5.8"}}
                   :mvn/repos mvn/standard-repos} nil)
    {:extra-paths ["test"]})

  (clojure.pprint/pprint
    (resolve-deps {:deps {'org.clojure/tools.analyzer.jvm {:mvn/version "0.6.9"}}
                   :mvn/repos mvn/standard-repos} nil))

  (clojure.pprint/pprint
    (resolve-deps {:deps {'cheshire/cheshire {:mvn/version "5.7.0"}}
                   :mvn/repos mvn/standard-repos} nil))

  ;; top deps win
  (clojure.pprint/pprint
    (resolve-deps {:deps {'org.clojure/clojure {:mvn/version "1.2.0"}
                          'cheshire/cheshire {:mvn/version "5.8.0"}}
                   :mvn/repos mvn/standard-repos} nil))

  ;; deps replacement
  (clojure.pprint/pprint
    (resolve-deps {:deps {'cheshire/cheshire {:mvn/version "5.8.0"}}
                   :mvn/repos mvn/standard-repos}
      {:deps {'org.clojure/tools.gitlibs {:mvn/version "0.2.64"}}}))

  ;; deps addition
  (clojure.pprint/pprint
    (resolve-deps {:deps {'cheshire/cheshire {:mvn/version "5.8.0"}}
                   :mvn/repos mvn/standard-repos}
      {:extra-deps {'org.clojure/tools.gitlibs {:mvn/version "0.2.64"}}}))

  ;; validate paths
  (make-classpath-map
    {:paths ["../foo/src"]}
    (resolve-deps {:deps {} :mvn/repos mvn/standard-repos} nil)
    nil)

  ;; override-deps
  (make-classpath-map
    {:paths ["src"]}
    (resolve-deps
      {:deps {'org.clojure/core.memoize {:mvn/version "0.5.8"}}
       :mvn/repos mvn/standard-repos}
      {:override-deps {'org.clojure/clojure {:mvn/version "1.3.0"}}})
    nil)

  (make-classpath-map
    {:paths ["src"]}
    (resolve-deps
      {:deps {'org.clojure/tools.deps.alpha {:mvn/version "0.1.40"}
              'org.clojure/clojure {:mvn/version "1.9.0-alpha17"}}
       :mvn/repos mvn/standard-repos} nil) nil)

  ;; extra paths
  (make-classpath-map
    {:paths ["src"]}
    (resolve-deps
      {:deps {'org.clojure/clojure {:mvn/version "1.10.0"}}
       :mvn/repos mvn/standard-repos} nil)
    {:extra-paths ["extra"]})

  ;; classpath overrides
  (make-classpath-map
    {}
    (resolve-deps
      {:deps {'org.clojure/tools.deps.alpha {:mvn/version "0.1.40"}
              'org.clojure/clojure {:mvn/version "1.9.0-alpha17"}}
       :mvn/repos mvn/standard-repos} nil)
    '{:classpath-overrides {org.clojure/clojure "foo"}})

  (resolve-deps
    {:deps '{org.clojure/clojure {:mvn/version "1.9.0"}
             org.clojure/clojurescript {:mvn/version "1.9.946"}
             reagent/reagent {:mvn/version "0.6.0"}}
     :mvn/repos mvn/standard-repos}
    nil)

  ;; err case
  (resolve-deps {:deps {'bogus/bogus {:mvn/version "1.2.3"}}
                 :mvn/repos mvn/standard-repos} nil)

  (resolve-deps {:deps {'bogus/bogus "1.2.3"}
                 :mvn/repos mvn/standard-repos} nil)

  (require
    '[clojure.tools.deps.alpha.extensions.git]
    '[clojure.tools.deps.alpha.extensions.deps])

  (resolve-deps
    {:deps {'foo/bar {:git/url "https://github.com/clojure/core.async.git"
                      :sha "ecea2539a724a415b15e50f12815b4ab115cfd35"}}}
    nil)

  (require '[clojure.tools.deps.alpha.util.session :as session])
  (time
    (do
      (session/with-session
        (resolve-deps
          {:deps {'com.google.cloud/google-cloud-monitoring {:mvn/version "1.78.0"}}
           :mvn/repos (merge mvn/standard-repos
                        {"datomic-cloud" {:url "s3://datomic-releases-1fc2183a.s3-us-east-1.amazonaws.com/maven/releases"}})}
          nil))
      nil))

  )
