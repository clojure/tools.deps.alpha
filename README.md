tools.deps.alpha
========================================

A functional API for transitive dependency graph expansion and the creation of classpaths.

# Rationale

Clojure "endeavors to be a general-purpose language suitable in those areas where Java is suitable" (from [Rationale](https://clojure.org/about/rationale)). To effectively target the JVM platform, Clojure needs to provide ready access to Java libraries, ideally in a way suited for dynamic development. In practice, this means meeting the JVM platform in two places:

* the classpath used when invoking JVM processes (and/or URLClassLoaders)
* transitive dependency download and resolution from Maven repositories

tools.deps.alpha aims to provide a functional API to access these capabilities.

tools.deps.alpha makes it simple and easy to interactively consume JVM libraries, without dragging in unrelated concerns of building programs or project management. (It should also be a useful shared resource for future work on project and build tools.) 

tools.deps.alpha will support package installers for Clojure (e.g. brew, apt-get, etc) to provide a path for Clojure installation and ongoing Clojure development.

The Clojure 1.9 release for the first time requires multiple artifacts to run Clojure (clojure, spec.alpha, and core.specs.alpha) and thus the issues of transitive dependency are now immediately in front of a Clojure user in the first minute.

Maven-artifacts-first orientation of current tooling has created great rigidity and friction for dev processes - making it hard to e.g. work with libs not yet building/publishing artifacts (new users!), working on speculative changes w/o artifacts, working on mutual changes across multiple libs, give control to a 3rd party tool to manage shared dependencies, and to directly leverage git which is now widely used as a source of truth for code lifecycles.

Also see:

* [Dependency Heaven](http://cdn.cognitect.com/presentations/2017/dependency_heaven.pdf) from EuroClojure 2017, video coming soon

# Releases Information

Not yet released.

[All Released Versions](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.clojure%22%20AND%20a%3A%22tools.deps.alpha%22)

# API 

For info on using tools.deps as a library, see:

* [API Usage](API.md)
* [API Docs](https://clojure.github.io/tools.deps.alpha/)

# `clj` script

The Clojure installer places tools.deps and its dependencies in your local Maven repository and also installs the following:

* `clj` - a Clojure runner script, placed on the PATH 
* ~/.clojure/deps.edn - a default deps.edn file that includes the latest stable version of Clojure as a dependency and standard Maven repositories
* ~/.clojure/deptools.cp - a classpath file to use when running tools.deps.alpha

There will be system installers for different platforms to take care of this. For now, see the following for manual installation.

## Manual installation

Goals:

* Create ~/.clojure
* Create ~/.clojure/deptools.cp
* Create ~/.clojure/deps.edn
* Add clj to path

Process:

```shell
git clone https://github.com/clojure/tools.deps.alpha.git
cd tools.deps.alpha

mkdir $HOME/.clojure

mvn dependency:build-classpath -Dmdep.outputFile=$HOME/.clojure/deptools.cp

cat <<EOF > $HOME/.clojure/deps.edn
{:deps {org.clojure/clojure {:type :mvn :version "1.8.0"}}
 :providers {:mvn {:repos {"central" {:url "https://repo1.maven.org/maven2/"}
                           "clojars" {:url "https://clojars.org/repo/"}}}}}
EOF

export PATH=$PATH:$PWD/src/main/resources/clj
```

## Command line options

Usage: `clj [<jvm_opts>] [<dep_opts>] [<main_opts>]`

where:

* `jvm_opts` is 0 or more of the following:
** `-D...` - sets a system property in the JVM, ex: -Dfoo=bar
** `-X...` - sets a JVM runtime setting, ex: -Xmx256m
** `-Jopt` - passes `opt` through to the JVM, ex: -J-server
* `dep_opts` is any of the following (but each at most once):
** `-Ralias...` - concatenated resolve-args aliases, ex: -R:bench:1.9
** `-Calias...` - concatenated classpath-override aliases, ex: -C:dev
** `-Plib=path,...` - comma-delimited, lib=path pairs specifying classpath overrides. Note: disables caching!
* `main_opts` are the `clojure.main` arguments, see [docs](https://clojure.org/reference/repl_and_main)

The `clj` script constructs and invokes a command-line of the form:

```shell
java <java_opts> -cp <classpath> clojure.main <main_opts>
```

The `dep_opts` are used to compute the `<classpath>`. Classpaths are cached (except when using `-P`) - see the
section on classpath caching below for more details. When a classpath is not available, the following process is
used to construct the classpath:

* Compute the deps map
  * Read the ~/.clojure/deps.edn file
  * If a local edn file exists at ./deps.edn, read that file
  * Combine these two maps with `merge`
* Compute the resolve-deps args
  * If `-R` specifies one or more aliases, find each alias in the deps map `:aliases`
  * `merge-with` `merge` the alias maps - the result is the resolve-args map
* Invoke `resolve-deps` with deps map and resolve-args map
* Write the libs map to the classpath cache
* Compute the classpath-overrides map
  * If `-C` specifies one or more aliases, find each alias in the deps map `:aliases`
  * If `-P` specifies a map of lib to path, add this as a trailing overrides map
  * `merge` the classpath-override alias maps
* Invoke `make-classpath` with the libs map returned by `resolve-deps` and the classpath-overrides map
* Write the classpath to the classpath cache
* Print the computed classpath to stdout

## deps.edn

The deps.edn file has the following format:

```clojure
{:deps {<lib> <coord>, ...}
 :aliases {<alias> <resolve-args-or-classpath-overrides>, ...}
 :providers {<provider-type> <provider-config>}}
```

where:

* `<lib>` is a symbol of the form `<groupId>/<artifactId>` or just `<artifact-and-groupId>`
* `<coord>` is a map with keys `:type` and (optionally) `:version` where the only initial type is `:mvn`
* `<alias>` is a keyword
* `<resolve-args-or-classpath-overrides> is:
** resolve-args: map with any of these optional keys. The value for each is a map from lib to coord.
*** `:extra-deps` - dependencies to add to the initial set
*** `:override-deps` - if dep is found when expanding deps, use this coordinate, regardless of what is specified
*** `:default-deps` - if dep is found when expanding deps, and no coordinate is provided, use this
** classpath-overrides: map from lib to path
* `<provider-type>` - matches the coord type, ie `:mvn`
* `<provider-config>` - depends on provider type, but example is `{:repos {"central" {:url "..."}}}`

_Note: see [API Usage](API.md) for specs, shorthand used here._

## Classpath caching

_Note: implementation is in flux, changes coming soon._

Classpath files are cached in the current directory under `.cpcache/`. File are of two forms:

* `.cpcache/<resolve-aliases>.libs`
* `.cpcache/<resolve-aliases>/<classpath-aliases>.cp`

where the `<resolve-aliases>` are either the `-R` aliases or `default`. The `<classpath-aliases>` are either the `-C` aliases or `default`.

The cached classpath file is used when:

* It exists
* It is newer than `deps.edn`
* It is newer than the libs file
* `-P` is NOT in use

The cached libs file is used when:

* It exists
* It is newer than `deps.edn`
* `-P` is NOT in use

## Examples

* Invoke: `clj`
* Given: No deps.edn file in the current directory.
* Result: Start a repl using the default deps file at ~/.clojure/deps.edn.

---

* Invoke: `clj`
* Given: A deps.edn file in the current directory.
* Result: Start a repl using the deps.edn file at ./deps.edn.

---

* Invoke: `clj -m my.app 1 2 3`
* Result: Load the my.app namespace and invoke my.app/-main with the arguments `1 2 3`. If a deps.edn file exists, use it, otherwise use the default deps file.

---

* Invoke: `clj -R:bench`
* Given: A deps.edn file like the one below.
* Result: Start a repl using the deps and add the extra deps defined by the `:bench` alias.

deps.edn:

```clojure
{:deps {org.clojure/clojure {:type :mvn :version "1.8.0"}}
 :aliases {:bench {:extra-deps {criterium {:type :mvn :version "0.4.4"}}}}}
```

---

* Invoke: `clj -R:bench,1.9`
* Given: A deps.edn file like the one below.
* Result: Start a repl using the deps and add the extra deps defined by the `:bench` alias and the override deps defined by the `:1.9` alias.

deps.edn:

```clojure
{:deps {org.clojure/clojure {:type :mvn :version "1.8.0"}}
 :aliases {:1.9 {:override-deps {org.clojure/clojure {:type :mvn :version "1.9.0-alpha17"}}}
           :bench {:extra-deps {criterium {:type :mvn :version "0.4.4"}}}}}
```

---

* Invoke: `clj -R1.9 -Cdev`
* Given: A deps.edn file like the one below.
* Result: Start a repl using the deps, the override deps defined by the `:1.9` alias, and the classpath override for the dev path.

deps.edn:

```clojure
{:deps {org.clojure/clojure {:type :mvn :version "1.8.0"}}
 :aliases {:1.9 {:override-deps {org.clojure/clojure {:type :mvn :version "1.9.0-alpha17"}}}
           :dev {org.clojure/clojure "/Users/me/code/clojure/target/classes"}}}
```

---

* Invoke: `clj -Porg.clojure=/Users/me/code/clojure/target/classes`
* Given: A deps.edn file like the one below.
* Result: Start a repl using the deps and the classpath override for the lib. The cache is never used when `-P` is used on the command-line.

deps.edn:

```clojure
{:deps {org.clojure/clojure {:type :mvn :version "1.9.0-alpha17"}}}
```

# Developer Information

* [GitHub project](https://github.com/clojure/tools.deps.alpha)

* [How to contribute](https://dev.clojure.org/display/community/Contributing)

* [Bug Tracker](https://dev.clojure.org/jira/browse/TDEPS)

* [Continuous Integration](https://build.clojure.org/job/tools.deps.alpha/)

* [Compatibility Test Matrix](https://build.clojure.org/job/tools.deps.alpha-test-matrix/)

# Copyright and License

Copyright © 2017 Rich Hickey, Alex Miller, and contributors

All rights reserved. The use and
distribution terms for this software are covered by the
[Eclipse Public License 1.0] which can be found in the file
epl-v10.html at the root of this distribution. By using this software
in any fashion, you are agreeing to be bound by the terms of this
license. You must not remove this notice, or any other, from this
software.

[Eclipse Public License 1.0]: http://opensource.org/licenses/eclipse-1.0.php
