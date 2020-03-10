Changelog
===========

*Also see [Tools and installer changelog](https://github.com/clojure/brew-install/blob/1.10.1/CHANGELOG.md)*

* 0.8.677 on Feb 14, 2020
  * TDEPS-150 - Fix regression in supporting -Scp flag (avoid resolving deps)
  * TDEPS-148 - Fix incorrect path resolution for git/local dep without deps.edn
* 0.8.661 on Jan 30, 2020 
  * New: concurrent downloading of deps and metadata
  * Fix regression in -Spom no longer populating <repositories> from 0.8.573
  * Fix manifest type lookup for git deps after coordinate resolution
* 0.8.640 on Jan 20, 2020
  * Added tests for pom deps and gen pom
* 0.8.633 on Jan 17, 2020
  * Fix regression with reading pom models with dep set from 0.8.624
  * Fix regression breaking pom gen from 0.8.624
* 0.8.624 on Jan 16, 2020 
  * New s3 transporter, replacing old s3 repo access
  * Bump all dep versions
* 0.8.599 on Nov 19, 2019
  * TDEPS-127 - Include pom resource directories in dep paths
  * TDEPS-140 - Use Maven server HTTP header properties
* 0.8.591 on Nov 17, 2019
  * Add tracing
  * TDEPS-143 - Fix pom gen in combination with aliases does not respect alias modifications
* 0.8.584 on Nov 4, 2019
  * TDEPS-142 - Fix :paths replacement doesn't work when :deps replacement used in alias
* 0.8.578 on Oct 18, 2019
  * Fix bugs in script.make-classpath2
* 0.8.573 on Oct 17, 2019
  * Deprecate script.generate-manifest in favor of generate-manifest2, reuse more of make-classpath2
* 0.8.567 on Oct 17, 2019 
  * Rework impl of :deps and :paths in aliases, deprecate script.make-classpath
    in favor of script.make-classpath2 and added tests
  * nil repo configs will be ignored (can remove default repos by declaring nil)
* 0.8.559 on Oct 15, 2019
  * TDEPS-138 - add tools.deps.alpha.reader/user-deps-location function
  * TDEPS-29 - fix -Spom adding unnecessary newlines in Java 9+
  * Add :deps and :paths to aliases to "replace" main :deps and :paths
* 0.7.549 on Sep 17, 2019
  * Fix: spec error for :local/root and missing required key :sha for git coords
* 0.7.541 on Aug 9, 2019
  * Fix: pom deps should include only compile and runtime deps
  * Fix: remove slf4j-nop as dep - let consumers choose
* 0.7.537 on Aug 9, 2019 
  * Fix: exclusions were not canonicalized and may fail to match and exclude
  * Perf: cache Maven resources and use Maven repository cache
* 0.7.527 on July 17, 2019
  * Deprecate and warn for clojure.tools.deps.alpha.reader/clojure-env
  * TDEPS-134 - use Maven mirrors
  * Change unknown aliases from error to warning
* 0.7.516 on June 29, 2019
  * Add ctda.reader/default-deps to duplicate the deps.edn file logic in clj
* 0.7.511 on June 29, 2019
  * Fix regression on default clojure version in install
* 0.7.505 on June 28, 2019
  * TDEPS-52 Embed install deps.edn, fix path gen on deps with no paths
  * TDEPS-96 Generate pom source path relative to transitive root dir
  * TDEPS-88 Resolve meaning of -Srepro (include install deps, ignore ~/.clojure)
* 0.6.496 on Mar 16, 2019
  * TDEPS-74 Fix resolution of relative paths in transitive local deps
  * TDEPS-20 Support Maven proxy settings
* 0.6.488 on Feb 13, 2019
  * TDEPS-114 Canonicalize Maven RELEASE or LATEST version marker
  * Add error handling for unresolvable Maven version
* 0.6.480 on Jan 4, 2019
  * TDEPS-112 Make exclusions classifier aware
* 0.6.474 on Jan 2, 2019
  * Error handling improvements when Maven artifact is not found
  * Error handling improvements for invalid deps.edn
  * TDEPS-50, TDEPS-109 Fixed handling of Maven classifiers
    * Specify classifier in lib name: groupId/artifactId$classifier
	* :classifier in coordinate map will now error
  * TDEPS-79, TDEPS-109 fix infinite loop in resolving deps tree
  * TDEPS-107 canonicalized exclusion groupIds in pom gen
* 0.5.460 on Oct 17, 2018
  * Use newer version of aws-maven under s3-wagon-private
* 0.5.452 on Sept 15, 2018 
  * TDEPS-92 error and report on unknown alias key
  * TDEPS-12 add support for multiple classifiers in libs
  * TDEPS-95 fix bug preventing :default-deps from working
* 0.5.442 on July 19, 2018
  * TDEPS-85 error on unknown aliases
* 0.5.435 on May 11, 2018 
  * TDEPS-9 support Maven deps in authenticated repositories
  * Use embedded pom to traverse transitive deps for a local dep jar
* 0.5.425 on Apr 13, 2018 
  * TDEPS-73 detect invalid deps.edn file and error
  * TDEPS-61 use Clojars CDN repo
  * TDEPS-26 include transitive runtime deps in addition to compile deps
* 0.5.417 on Mar 23, 2018
  * TDEPS-59 clear cached jvm and main opts if stale
* 0.5.410 on Mar 16, 2018
  * TDEPS-54 add -Sdeps and -A support to -Spom
  * TDEPS-55 fix -Spom for xml file with comment before root node
  * TDEPS-40 clean up log dependencies
  * Update to tools.gitlibs 0.2.64
* 0.5.398 on Feb 22, 2018
  * Update dependency versions to latest
* 0.5.393 on Feb 21, 2018
  * Change call to clojure -Senv to -Sdescribe
* 0.5.388 on Feb 20, 2018
  * Better error if cache dir can't be created
  * API additions and refactoring
* 0.5.373 on Feb 10, 2018 
  * Add tools.deps.alpha/lib-location method
  * Load all extensions in tools.deps.alpha
  * Add support in make-classpath for skipping classpath and lib writing
* 0.5.351 on Feb 2, 2018 
  * Alias support for main, jvm opts, and generic aliases
  * TDEPS-38 Fix issues with merging non-maps at top-level
  * Automatically add src/main/clojure as a source path in pom manifest reader
* 0.5.342 on Jan 23, 2018
  * TDEPS-19 Add support for command line deps config data
  * Add print-tree script
  * Fix bug with git deps with :deps/root
* 0.5.323 on Jan 18, 2018
  * Remove stray debug print in -Spom
  * Fix source paths in pom manifest reader
* 0.5.317 on Jan 18, 2018
  * TDEPS-24 Major changes to expansion and version resolution
  * TDEPS-32 git extension should use :deps/root if specified
  * Change error handling to dump stack on unexpected exception
  * Add pom file reader for local and git deps
  * TDEPS-34 Emit exclusions and classifiers in pom gen
  * Tighten git sha comparison rules
* 0.4.295 on Jan 10, 2018
  * Disallow prefix sha in git coord
  * TDEPS-28 - -Spom - now supports -R and -C aliases (for top-level deps)
  * TDEPS-18 - Internal refactoring for script arg handling
* 0.4.277 on Jan 8, 2018
  * Add code for resolve-tags command
  * Git deps now use :sha and :tag rather than :rev
  * TDEPS-27 - pom gen reported warning when run on Java 9
  * Less confusing errors when a git dep has no deps.edn
* 0.3.260 on Jan 4, 2018
  * TDEPS-25 - pom gen will now sync repositories
  * Update to latest version of gitlibs
* 0.3.254 on Jan 3, 2018
  * Refactored gitlibs out into library
* 0.3.231 on Dec 21, 2017
  * Insert deps config at front of config chain rather than back
  * Make deps manifest reader tolerant of missing deps.edn in project
* 0.3.225 on Dec 21, 2017
  * Fix bug in cache dir configuration
* 0.3.220 on Dec 20, 2017
  * Added support for git deps - see docs for details
  * Print warnings and messages to stderr rather than stdout
* 0.2.196 on Dec 7, 2017
  * Local deps should use full deps file chain, not just project deps
  * Change default clojars url back
* 0.2.190 on Dec 2, 2017
  * Change default clojars url to use the CDN
  * Fix bug in :local/root for jar manifests
* 0.2.183 on Nov 8, 2017
  * Improve error handling when switching on coordinate and manifest types
  * Close pom reader before writing during pom gen sync
* 0.2.173 on Oct 31, 2017
  * TDEPS-14 - Canonicalize lib symbol format
* 0.2.167 on Oct 26, 2017
  * Add s3 Maven repo support
* 0.2.162 on Oct 24, 2017
  * Add pom gen/sync
* 0.2.155 on Oct 6, 2017
  * Overhaul provider extensions
  * Implement local provider deps project support
* 0.2.130 on Sep 21, 2017
  * Always treat libs-file as stale
* 0.2.122 on Sep 21, 2017
  * Ignore reserved "deps" namespace when dispatching on coord type
  * Avoid duplicating cache logic in makecp
* 0.2.116 on Sep 20, 2017
  * New strategy for coordinate specification and dispatch
* 0.1.108 on Sep 19, 2017
  * Remove -P classpath overrides option handling
* 0.1.103 on Sep 19, 2017
  * Bug fixes for :paths and :extra-paths
* 0.1.85 on Sep 15, 2017
  * TDEPS-11 - Lift provider attributes up to top-level with namespace qualifiers
  * Use platform-specific character when creating classpaths
  * Add top-level :paths and make-classpath :extra-paths
* 0.1.78 on Sep 12, 2017
  * TDEPS-10 - Change makecp to take a list of config files to be merged left to right rather than just a user and project level file. Changed args to be named rather than positional to help with script evolution.
* 0.1.73 on Aug 31, 2017
  * Combine user and project deps.edn with `merge-with merge` rather than `merge`
* 0.1.68 on Aug 25, 2017
  * Top dep coords take priority over transitive deps
* 0.1.62 on Aug 24, 2017
  * Omit scope and optional attributes on expanded Maven deps
  * Handle exclusions in resolve-deps
* 0.1.56 on Aug 22, 2017
  * Add specs
  * Add provider-specific version comparison
  * Add Maven version comparison
  * Report exclusions in Maven coords
* 0.1.40 on Aug 14, 2017
  * makecp now takes an initial argument that is the system deps file to avoid all implicit dirs
* 0.1.35 on Aug 14, 2017
  * Load prototype :file provider in addition to the :mvn provider
  * clj script has been deprecated and moved to the brew-install repo (but is still here for the moment)
* 0.1.29 on Aug 1, 2017
  * Modify clj script to check whether ~/.clojure/clj.props has changed and if so, re-run install-clj
  * TDEPS-2 Overhauled makecp error handling
  * Make rlwrap usage dependent on availability
  * Rename deptools.cp to clj.cp
* 0.1.14 on Jul 24, 2017
  * Initial release
