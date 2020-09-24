{:namespaces
 ({:doc nil,
   :name "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha/clojure.tools.cli.api-api.html",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/f1afae14c6d6d1b3a6faf15f2748043ce6d8eee1/src/main/clojure/clojure/tools/cli/api.clj"}
  {:doc nil,
   :name "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha/clojure.tools.deps.alpha-api.html",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj"}),
 :vars
 ({:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/f1afae14c6d6d1b3a6faf15f2748043ce6d8eee1/src/main/clojure/clojure/tools/cli/api.clj",
   :name "mvn-install",
   :file "src/main/clojure/clojure/tools/cli/api.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/f1afae14c6d6d1b3a6faf15f2748043ce6d8eee1/src/main/clojure/clojure/tools/cli/api.clj#L106",
   :line 106,
   :var-type "function",
   :arglists ([{:keys [lib jar pom classifier local-repo], :as opts}]),
   :doc
   "Install a jar and optional pom to the Maven local cache.\nThe group/artifact/version coordinate will be pulled from the\npom if supplied, or the pom in the jar file, or must be provided.\nAny provided attributes override those in the pom or jar.\n\nOptions:\n  :jar (required) - path to jar file\n  :pom (optional) - path to pom file\n  :lib (optional) - qualified symbol like my.org/lib\n  :version (optional) - string\n  :classifier (optional) - string\n  :local-repo (optional) - path to local repo (default = ~/.m2/repository)\n\nExecute ad-hoc:\n  clj -X:deps mvn/install :jar '\"foo.jar\"'",
   :namespace "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.api-api.html#clojure.tools.cli.api/mvn-install"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/f1afae14c6d6d1b3a6faf15f2748043ce6d8eee1/src/main/clojure/clojure/tools/cli/api.clj",
   :name "mvn-pom",
   :file "src/main/clojure/clojure/tools/cli/api.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/f1afae14c6d6d1b3a6faf15f2748043ce6d8eee1/src/main/clojure/clojure/tools/cli/api.clj#L52",
   :line 52,
   :var-type "function",
   :arglists ([{:keys [argmaps]}]),
   :doc
   "Sync local pom.xml.\nOptions:\n  :argmaps - vector of aliases to combine into argmaps to resolve-deps and make-classpath",
   :namespace "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.api-api.html#clojure.tools.cli.api/mvn-pom"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/f1afae14c6d6d1b3a6faf15f2748043ce6d8eee1/src/main/clojure/clojure/tools/cli/api.clj",
   :name "tree",
   :file "src/main/clojure/clojure/tools/cli/api.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/f1afae14c6d6d1b3a6faf15f2748043ce6d8eee1/src/main/clojure/clojure/tools/cli/api.clj#L38",
   :line 38,
   :var-type "function",
   :arglists ([_]),
   :doc "Print deps tree. Takes an opts map, but no opts yet.",
   :namespace "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.api-api.html#clojure.tools.cli.api/tree"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "calc-basis",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L604",
   :line 604,
   :var-type "function",
   :arglists
   ([master-edn] [master-edn {:keys [resolve-args classpath-args]}]),
   :doc
   "Calculates and returns the runtime basis from a master deps edn map, modifying\n resolve-deps and make-classpath args as needed.\n\n  master-edn - a master deps edn map\n  args - an optional map of arguments to constituent steps, keys:\n    :resolve-args - map of args to resolve-deps, with possible keys:\n      :extra-deps\n      :override-deps\n      :default-deps\n      :threads - number of threads to use during deps resolution\n      :trace - flag to record a trace log\n    :classpath-args - map of args to make-classpath-map, with possible keys:\n      :extra-paths\n      :classpath-overrides\n\nReturns the runtime basis, which is the initial deps edn map plus these keys:\n  :resolve-args - the resolve args passed in, if any\n  :classpath-args - the classpath args passed in, if any\n  :libs - lib map, per resolve-deps\n  :classpath - classpath map per make-classpath-map\n  :classpath-roots - vector of paths in classpath order",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/calc-basis"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "combine-aliases",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L184",
   :line 184,
   :var-type "function",
   :arglists ([edn-map alias-kws]),
   :doc
   "Find, read, and combine alias maps identified by alias keywords from\na deps edn map into a single args map.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/combine-aliases"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "find-edn-maps",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L116",
   :line 116,
   :var-type "function",
   :arglists ([] [project-edn-file]),
   :doc
   "Finds and returns standard deps edn maps in a map with keys\n  :root-edn, :user-edn, :project-edn\nIf no project-edn is supplied, use the deps.edn in current directory",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/find-edn-maps"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "join-classpath",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L571",
   :line 571,
   :var-type "function",
   :arglists ([roots]),
   :doc
   "Takes a coll of string classpath roots and creates a platform sensitive classpath",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/join-classpath"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "lib-location",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L192",
   :line 192,
   :var-type "function",
   :arglists ([lib coord deps-config]),
   :doc
   "Find the file path location of where a lib/coord would be located if procured\nwithout actually doing the procuring!",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/lib-location"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "make-classpath",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L576",
   :line 576,
   :deprecated "0.9.745",
   :var-type "function",
   :arglists ([lib-map paths classpath-args]),
   :doc
   "Takes a lib map, and a set of explicit paths. Extracts the paths for each chosen\nlib coordinate, and assembles a classpath string using the system path separator.\nThe classpath-args is a map with keys that can be used to modify the classpath\nbuilding operation:\n\n  :extra-paths - extra classpath paths to add to the classpath\n  :classpath-overrides - a map of lib to path, where path is used instead of the coord's paths\n\nReturns the classpath as a string.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/make-classpath"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "make-classpath-map",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L552",
   :line 552,
   :var-type "function",
   :arglists
   ([{:keys [paths aliases], :as deps-edn-map}
     lib-map
     classpath-args]),
   :doc
   "Takes a merged deps edn map and a lib map. Extracts the paths for each chosen\nlib coordinate, and assembles a classpath map. The classpath-args is a map with\nkeys that can be used to modify the classpath building operation:\n  :extra-paths - extra classpath paths to add to the classpath\n  :classpath-overrides - a map of lib to path, where path is used instead of the coord's paths\n\nReturns a map:\n  :classpath map of path entry (string) to a map describing where its from,  either a :lib-name or :path-key entry.\n  :classpath-roots coll of the classpath keys in classpath order",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/make-classpath-map"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "merge-edns",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L139",
   :line 139,
   :var-type "function",
   :arglists ([deps-edn-maps]),
   :doc
   "Merge multiple deps edn maps from left to right into a single deps edn map.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/merge-edns"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "print-tree",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L499",
   :line 499,
   :var-type "function",
   :arglists ([lib-map]),
   :doc "Print lib-map tree to the console",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/print-tree"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "resolve-deps",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L457",
   :line 457,
   :var-type "function",
   :arglists ([deps-map args-map]),
   :doc
   "Takes a deps configuration map and resolves the transitive dependency graph\nfrom the initial set of deps. args-map is a map with several keys (all\noptional) that can modify the results of the transitive expansion:\n\n  :extra-deps - a map from lib to coord of deps to add to the main deps\n  :override-deps - a map from lib to coord of coord to use instead of those in the graph\n  :default-deps - a map from lib to coord of deps to use if no coord specified\n  :trace - boolean. If true, the returned lib map will have metadata with :trace log\n  :threads - long. If provided, sets the number of concurrent download threads\n\nReturns a lib map (map of lib to coordinate chosen).",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/resolve-deps"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "root-deps",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L97",
   :line 97,
   :var-type "function",
   :arglists ([]),
   :doc
   "Read the root deps.edn resource from the classpath at the path\nclojure/tools/deps/deps.edn",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/root-deps"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "slurp-deps",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L91",
   :line 91,
   :var-type "function",
   :arglists ([dep-file]),
   :doc
   "Read a single deps.edn file from disk and canonicalize symbols,\nreturn a deps map.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/slurp-deps"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "tool",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L590",
   :line 590,
   :var-type "function",
   :arglists ([project-edn tool-args]),
   :doc
   "Transform project edn for tool by applying tool args (keys = :paths, :deps) and\nreturning an updated project edn.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/tool"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "user-deps-path",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/dc5a70dd348929fc2431b0e939e75bb424aeb224/src/main/clojure/clojure/tools/deps/alpha.clj#L104",
   :line 104,
   :var-type "function",
   :arglists ([]),
   :doc
   "Use the same logic as clj to calculate the location of the user deps.edn.\nNote that it's possible no file may exist at this location.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/user-deps-path"})}
