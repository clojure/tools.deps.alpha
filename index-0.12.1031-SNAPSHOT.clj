{:namespaces
 ({:doc
   "This api provides functions that can be executed from the Clojure tools using -X:deps.",
   :name "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha/clojure.tools.cli.api-api.html",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj"}
  {:doc nil,
   :name "clojure.tools.cli.help",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha/clojure.tools.cli.help-api.html",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/bb7b22bdc8995baf3db7aa22a19c9952671dae41/src/main/clojure/clojure/tools/cli/help.clj"}
  {:doc nil,
   :name "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha/clojure.tools.deps.alpha-api.html",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj"}
  {:doc nil,
   :name "clojure.tools.deps.alpha.tool",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha/clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha.tool",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj"}
  {:doc nil,
   :name "clojure.tools.deps.alpha.tree",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha/clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha.tree",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/271dbc26e4150ec97710c9eb123ed95d240ff8e2/src/main/clojure/clojure/tools/deps/alpha/tree.clj"}),
 :vars
 ({:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj",
   :name "basis",
   :file "src/main/clojure/clojure/tools/cli/api.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj#L37",
   :line 37,
   :var-type "function",
   :arglists ([params]),
   :doc
   "Create a basis from a set of deps sources and a set of aliases. By default, use\n root, user, and project deps and no argmaps (essentially the same classpath you get by\n default from the Clojure CLI).\n\n Each dep source value can be :standard, a string path, a deps edn map, or nil.\n Sources are merged in the order - :root, :user, :project, :extra.\n\n Aliases refer to argmaps in the merged deps that will be supplied to the basis\n subprocesses (tool, resolve-deps, make-classpath-map).\n\n The following subprocess argmap args can be provided:\n   Key                  Subproc             Description\n   :replace-deps        tool                Replace project deps\n   :replace-paths       tool                Replace project paths\n   :extra-deps          resolve-deps        Add additional deps\n   :override-deps       resolve-deps        Override coord of dep\n   :default-deps        resolve-deps        Provide coord if missing\n   :extra-paths         make-classpath-map  Add additional paths\n   :classpath-overrides make-classpath-map  Replace lib path in cp\n\n Options:\n   :root    - dep source, default = :standard\n   :user    - dep source, default = :standard\n   :project - dep source, default = :standard (\"./deps.edn\")\n   :extra   - dep source, default = nil\n   :aliases - coll of aliases of argmaps  to apply to subprocesses\n\nReturns {:basis basis}, which basis is initial deps edn map plus these keys:\n  :resolve-args - the resolve args passed in, if any\n  :classpath-args - the classpath args passed in, if any\n  :libs - lib map, per resolve-deps\n  :classpath - classpath map per make-classpath-map\n  :classpath-roots - vector of paths in classpath order",
   :namespace "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.api-api.html#clojure.tools.cli.api/basis"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj",
   :name "find-versions",
   :file "src/main/clojure/clojure/tools/cli/api.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj#L270",
   :line 270,
   :var-type "function",
   :arglists ([{:keys [lib tool], :as args}]),
   :doc
   "Find available tool versions given either a lib (with :lib) or\nexisting installed tool (with :tool). If lib, check all registered\nprocurers and print one coordinate per line when found.",
   :namespace "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.api-api.html#clojure.tools.cli.api/find-versions"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj",
   :name "git-resolve-tags",
   :file "src/main/clojure/clojure/tools/cli/api.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj#L149",
   :line 149,
   :var-type "function",
   :arglists ([_]),
   :doc "Resolve git tags in deps.edn git deps to full shas.",
   :namespace "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.api-api.html#clojure.tools.cli.api/git-resolve-tags"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj",
   :name "mvn-install",
   :file "src/main/clojure/clojure/tools/cli/api.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj#L229",
   :line 229,
   :var-type "function",
   :arglists
   ([{:keys [jar pom lib version classifier local-repo], :as opts}]),
   :doc
   "Install a jar and pom to the Maven local cache.\nThe pom file must either be supplied, or generated based\non provided lib/version/classifier, or provided inside the jar.\nThe group/artifact/version coordinate will be pulled from the\npom source as above.\n\nRequired:\n  :jar (reqired) - path to jar file (embedded pom used by default)\n\nExplicit pom options:\n  :pom - path to pom file (pom in jar ignored)\n\nGenerated pom options:\n  :lib - qualified symbol like my.org/lib\n  :version - string\n  :classifier - string\n\nOther options:\n  :local-repo (optional) - path to local repo (default = ~/.m2/repository)\n\nExecute ad-hoc:\n  clj -X:deps mvn/install :jar '\"foo-1.2.3.jar\"'",
   :namespace "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.api-api.html#clojure.tools.cli.api/mvn-install"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj",
   :name "mvn-pom",
   :file "src/main/clojure/clojure/tools/cli/api.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj#L156",
   :line 156,
   :var-type "function",
   :arglists ([{:keys [argmaps]}]),
   :doc
   "Sync or create pom.xml from deps.edn.\n\nOptions:\n  :argmaps - vector of aliases to combine into argmaps to resolve-deps and make-classpath",
   :namespace "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.api-api.html#clojure.tools.cli.api/mvn-pom"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj",
   :name "prep",
   :file "src/main/clojure/clojure/tools/cli/api.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj#L75",
   :line 75,
   :var-type "function",
   :arglists
   ([{:keys [basis force log], :or {log :info}, :as params}]),
   :doc
   "Prep the unprepped libs found in the transitive lib set of basis.\nIf no basis is provided, create and use the default project basis.\n\nOptions:\n  :basis - basis to prep. If not provided, use (create-basis nil).\n  :force - flag on whether to force prepped libs to re-prep (default = false)\n  :log - :none, :info (default), or :debug\n\nReturns params modified.",
   :namespace "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.api-api.html#clojure.tools.cli.api/prep"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj",
   :name "tree",
   :file "src/main/clojure/clojure/tools/cli/api.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/d3129d59168e56a6b96e5a72044c1a5622e9bef5/src/main/clojure/clojure/tools/cli/api.clj#L111",
   :line 111,
   :var-type "function",
   :arglists ([opts]),
   :doc
   "Print deps tree for the current project's deps.edn built from either the\ncurrent directory deps.edn, or if provided, the trace file.\n\nBy default, :format will :print to the console in a human friendly tree. Use\n:edn mode to print the tree to edn.\n\nIn print mode, deps are printed with prefix of either . (included) or X (excluded).\nA reason code for inclusion/exclusion may be added at the end of the line.\n\nInput options:\n  :file      Path to trace.edn file (from clj -Strace) to use in computing the tree\n\nOutput mode:\n  :format    :print (default) or :edn\n\nPrint output mode modifiers:\n  :indent    Indent spacing (default = 2)\n  :hide-libs Set of libs to hide as deps (if not top dep), default = #{org.clojure/clojure}",
   :namespace "clojure.tools.cli.api",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.api-api.html#clojure.tools.cli.api/tree"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/bb7b22bdc8995baf3db7aa22a19c9952671dae41/src/main/clojure/clojure/tools/cli/help.clj",
   :name "dir",
   :file "src/main/clojure/clojure/tools/cli/help.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/bb7b22bdc8995baf3db7aa22a19c9952671dae41/src/main/clojure/clojure/tools/cli/help.clj#L69",
   :line 69,
   :var-type "function",
   :arglists ([{:keys [ns], :as args}]),
   :doc
   "Prints a sorted directory of public vars in a namespace. If a namespace is not\nspecified :ns-default is used instead.",
   :namespace "clojure.tools.cli.help",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.help-api.html#clojure.tools.cli.help/dir"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/bb7b22bdc8995baf3db7aa22a19c9952671dae41/src/main/clojure/clojure/tools/cli/help.clj",
   :name "doc",
   :file "src/main/clojure/clojure/tools/cli/help.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/bb7b22bdc8995baf3db7aa22a19c9952671dae41/src/main/clojure/clojure/tools/cli/help.clj#L42",
   :line 42,
   :var-type "function",
   :arglists ([{:keys [ns fn], :as args}]),
   :doc
   "Print doc for the specified namespace or function. If neither is specified, print docs\nfor :ns-default.\n\nOptions:\n  :ns Print docs for namespace\n  :fn Print docs for function",
   :namespace "clojure.tools.cli.help",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.cli.help-api.html#clojure.tools.cli.help/doc"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "calc-basis",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L713",
   :line 713,
   :var-type "function",
   :arglists
   ([master-edn]
    [master-edn {:keys [resolve-args classpath-args], :as argmaps}]),
   :doc
   "Calculates and returns the runtime basis from a master deps edn map, modifying\n resolve-deps and make-classpath args as needed.\n\n  master-edn - a master deps edn map\n  args - an optional map of arguments to constituent steps, keys:\n    :resolve-args - map of args to resolve-deps, with possible keys:\n      :extra-deps\n      :override-deps\n      :default-deps\n      :threads - number of threads to use during deps resolution\n      :trace - flag to record a trace log\n    :classpath-args - map of args to make-classpath-map, with possible keys:\n      :extra-paths\n      :classpath-overrides\n\nReturns the runtime basis, which is the initial deps edn map plus these keys:\n  :resolve-args - the resolve args passed in, if any\n  :classpath-args - the classpath args passed in, if any\n  :libs - lib map, per resolve-deps\n  :classpath - classpath map per make-classpath-map\n  :classpath-roots - vector of paths in classpath order",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/calc-basis"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "combine-aliases",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L187",
   :line 187,
   :var-type "function",
   :arglists ([edn-map alias-kws]),
   :doc
   "Find, read, and combine alias maps identified by alias keywords from\na deps edn map into a single args map.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/combine-aliases"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "create-basis",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L760",
   :line 760,
   :var-type "function",
   :arglists
   ([{:keys [root user project extra aliases],
      :as params,
      :or {root :standard, user :standard, project :standard}}]),
   :doc
   "Create a basis from a set of deps sources and a set of aliases. By default, use\nroot, user, and project deps and no argmaps (essentially the same classpath you get by\ndefault from the Clojure CLI).\n\nEach dep source value can be :standard, a string path, a deps edn map, or nil.\nSources are merged in the order - :root, :user, :project, :extra.\n\nAliases refer to argmaps in the merged deps that will be supplied to the basis\nsubprocesses (tool, resolve-deps, make-classpath-map).\n\nThe following subprocess argmap args can be provided:\n  Key                  Subproc             Description\n  :replace-deps        tool                Replace project deps\n  :replace-paths       tool                Replace project paths\n  :extra-deps          resolve-deps        Add additional deps\n  :override-deps       resolve-deps        Override coord of dep\n  :default-deps        resolve-deps        Provide coord if missing\n  :extra-paths         make-classpath-map  Add additional paths\n  :classpath-overrides make-classpath-map  Replace lib path in cp\n\nOptions:\n  :root    - dep source, default = :standard\n  :user    - dep source, default = :standard\n  :project - dep source, default = :standard (\"./deps.edn\")\n  :extra   - dep source, default = nil\n  :aliases - coll of aliases of argmaps  to apply to subprocesses\n\nReturns a runtime basis, which is the initial merged deps edn map plus these keys:\n :resolve-args - the resolve args passed in, if any\n :classpath-args - the classpath args passed in, if any\n :libs - lib map, per resolve-deps\n :classpath - classpath map per make-classpath-map\n :classpath-roots - vector of paths in classpath order",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/create-basis"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "find-edn-maps",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L119",
   :line 119,
   :var-type "function",
   :arglists ([] [project-edn-file]),
   :doc
   "Finds and returns standard deps edn maps in a map with keys\n  :root-edn, :user-edn, :project-edn\nIf no project-edn is supplied, use the deps.edn in current directory",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/find-edn-maps"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "join-classpath",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L611",
   :line 611,
   :var-type "function",
   :arglists ([roots]),
   :doc
   "Takes a coll of string classpath roots and creates a platform sensitive classpath",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/join-classpath"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "lib-location",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L195",
   :line 195,
   :var-type "function",
   :arglists ([lib coord deps-config]),
   :doc
   "Find the file path location of where a lib/coord would be located if procured\nwithout actually doing the procuring!",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/lib-location"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "make-classpath",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L616",
   :line 616,
   :deprecated "0.9.745",
   :var-type "function",
   :arglists ([lib-map paths classpath-args]),
   :doc
   "Takes a lib map, and a set of explicit paths. Extracts the paths for each chosen\nlib coordinate, and assembles a classpath string using the system path separator.\nThe classpath-args is a map with keys that can be used to modify the classpath\nbuilding operation:\n\n  :extra-paths - extra classpath paths to add to the classpath\n  :classpath-overrides - a map of lib to path, where path is used instead of the coord's paths\n\nReturns the classpath as a string.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/make-classpath"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "make-classpath-map",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L592",
   :line 592,
   :var-type "function",
   :arglists ([deps-edn-map lib-map classpath-args]),
   :doc
   "Takes a merged deps edn map and a lib map. Extracts the paths for each chosen\nlib coordinate, and assembles a classpath map. The classpath-args is a map with\nkeys that can be used to modify the classpath building operation:\n  :extra-paths - extra classpath paths to add to the classpath\n  :classpath-overrides - a map of lib to path, where path is used instead of the coord's paths\n\nReturns a map:\n  :classpath map of path entry (string) to a map describing where its from,  either a :lib-name or :path-key entry.\n  :classpath-roots coll of the classpath keys in classpath order",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/make-classpath-map"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "merge-edns",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L142",
   :line 142,
   :var-type "function",
   :arglists ([deps-edn-maps]),
   :doc
   "Merge multiple deps edn maps from left to right into a single deps edn map.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/merge-edns"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "prep-libs!",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L671",
   :line 671,
   :var-type "function",
   :arglists ([lib-map {:keys [action log]} config]),
   :doc
   "Takes a lib map and looks for unprepped libs, optionally prepping them.\n\nOptions:\n  :action - what to do when an unprepped lib is found, one of:\n              :prep - if unprepped, prep\n              :force - prep regardless of whether already prepped\n              :error (default) - don't prep, error\n  :log -  print to console based on log level (default, no logging):\n            :info  - print only when prepping\n            :debug - :info + print for each lib considered",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/prep-libs!"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "print-tree",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L520",
   :line 520,
   :var-type "function",
   :arglists ([lib-map]),
   :doc "Print lib-map tree to the console",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/print-tree"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "resolve-deps",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L478",
   :line 478,
   :var-type "function",
   :arglists ([deps-map args-map]),
   :doc
   "Takes a deps configuration map and resolves the transitive dependency graph\nfrom the initial set of deps. args-map is a map with several keys (all\noptional) that can modify the results of the transitive expansion:\n\n  :extra-deps - a map from lib to coord of deps to add to the main deps\n  :override-deps - a map from lib to coord of coord to use instead of those in the graph\n  :default-deps - a map from lib to coord of deps to use if no coord specified\n  :trace - boolean. If true, the returned lib map will have metadata with :trace log\n  :threads - long. If provided, sets the number of concurrent download threads\n\nReturns a lib map (map of lib to coordinate chosen).",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/resolve-deps"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "root-deps",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L100",
   :line 100,
   :var-type "function",
   :arglists ([]),
   :doc
   "Read the root deps.edn resource from the classpath at the path\nclojure/tools/deps/deps.edn",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/root-deps"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "slurp-deps",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L93",
   :line 93,
   :var-type "function",
   :arglists ([dep-file]),
   :doc
   "Read a single deps.edn file from disk and canonicalize symbols,\nreturn a deps map. If the file doesn't exist, returns nil.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/slurp-deps"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "tool",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L630",
   :line 630,
   :var-type "function",
   :arglists ([project-edn tool-args]),
   :doc
   "Transform project edn for tool by applying tool args (keys = :paths, :deps) and\nreturning an updated project edn.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/tool"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj",
   :name "user-deps-path",
   :file "src/main/clojure/clojure/tools/deps/alpha.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/0221dfa5248a45d97938a17cbce0356f470e2812/src/main/clojure/clojure/tools/deps/alpha.clj#L107",
   :line 107,
   :var-type "function",
   :arglists ([]),
   :doc
   "Use the same logic as clj to calculate the location of the user deps.edn.\nNote that it's possible no file may exist at this location.",
   :namespace "clojure.tools.deps.alpha",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha/user-deps-path"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj",
   :name "install-tool",
   :file "src/main/clojure/clojure/tools/deps/alpha/tool.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj#L29",
   :line 29,
   :var-type "function",
   :arglists ([lib coord as]),
   :doc
   "Procure the lib+coord, install the tool to the user tools dir (with lib, coord)",
   :namespace "clojure.tools.deps.alpha.tool",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha.tool/install-tool"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj",
   :name "list-tools",
   :file "src/main/clojure/clojure/tools/deps/alpha/tool.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj#L69",
   :line 69,
   :var-type "function",
   :arglists ([]),
   :doc "Return seq of available tool names",
   :namespace "clojure.tools.deps.alpha.tool",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha.tool/list-tools"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj",
   :name "remove-tool",
   :file "src/main/clojure/clojure/tools/deps/alpha/tool.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj#L79",
   :line 79,
   :var-type "function",
   :arglists ([tool]),
   :doc
   "Removes tool installation, if it exists. Returns true if it exists and was deleted.",
   :namespace "clojure.tools.deps.alpha.tool",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha.tool/remove-tool"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj",
   :name "resolve-tool",
   :file "src/main/clojure/clojure/tools/deps/alpha/tool.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj#L47",
   :line 47,
   :var-type "function",
   :arglists ([tool]),
   :doc
   "Resolve a tool by name, look up and return:\n{:lib lib\n :coord coord}\nOr nil if unknown.",
   :namespace "clojure.tools.deps.alpha.tool",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha.tool/resolve-tool"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj",
   :name "usage",
   :file "src/main/clojure/clojure/tools/deps/alpha/tool.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/2b068b4ff9ab1cbbabe24e49eb22995e6cff1ad2/src/main/clojure/clojure/tools/deps/alpha/tool.clj#L57",
   :line 57,
   :var-type "function",
   :arglists ([tool]),
   :doc
   "Resolve a tool and return it's usage data, which may be nil.\nThrows ex-info if tool is unknown.",
   :namespace "clojure.tools.deps.alpha.tool",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha.tool/usage"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/271dbc26e4150ec97710c9eb123ed95d240ff8e2/src/main/clojure/clojure/tools/deps/alpha/tree.clj",
   :name "print-tree",
   :file "src/main/clojure/clojure/tools/deps/alpha/tree.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/271dbc26e4150ec97710c9eb123ed95d240ff8e2/src/main/clojure/clojure/tools/deps/alpha/tree.clj#L76",
   :line 76,
   :var-type "function",
   :arglists
   ([tree {:keys [indent], :or {indent 2}, :as opts}]
    [{:keys [children], :as tree} indented opts]),
   :doc
   "Print the tree to the console.\nOptions:\n  :indent    Indent spacing (default = 2)\n  :hide-libs Set of libs to ignore as deps under top deps, default = #{org.clojure/clojure}",
   :namespace "clojure.tools.deps.alpha.tree",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha.tree/print-tree"}
  {:raw-source-url
   "https://github.com/clojure/tools.deps.alpha/raw/271dbc26e4150ec97710c9eb123ed95d240ff8e2/src/main/clojure/clojure/tools/deps/alpha/tree.clj",
   :name "trace->tree",
   :file "src/main/clojure/clojure/tools/deps/alpha/tree.clj",
   :source-url
   "https://github.com/clojure/tools.deps.alpha/blob/271dbc26e4150ec97710c9eb123ed95d240ff8e2/src/main/clojure/clojure/tools/deps/alpha/tree.clj#L17",
   :line 17,
   :var-type "function",
   :arglists ([trace]),
   :doc
   "Convert a deps trace data structure into a deps tree.\n\nA deps tree has the structure of the full dependency expansion.\nEach node of the tree is a map from lib to coord-info with at least these keys:\n  :lib - library symbol\n  :coord - the coord map that was used (may not be the original coord if replaced\n           due to default-deps or override-deps)\n  :include - boolean of whether this node is included in the returned deps\n  :reason - why the node was or was not included\n  :children - vector of child nodes",
   :namespace "clojure.tools.deps.alpha.tree",
   :wiki-url
   "https://clojure.github.io/tools.deps.alpha//clojure.tools.deps.alpha-api.html#clojure.tools.deps.alpha.tree/trace->tree"})}
