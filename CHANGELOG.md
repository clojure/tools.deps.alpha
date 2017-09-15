Changelog
===========

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
