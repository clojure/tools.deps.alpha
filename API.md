tools.deps.alpha
========================================

A fuctional API for transitive dependency graph expansion and the creation of classpaths.

# Rationale

Clojure "endeavors to be a general-purpose language suitable in those areas where Java is suitable" (from [Rationale](https://clojure.org/about/rationale)). To effectively target the JVM platform, Clojure needs to provide ready access to Java libraries, ideally in a way suited for dynamic development. In practice, this means meeting the JVM platform in two places:

* the classpath used when invoking JVM processes (and/or URLClassLoaders)
* transitive dependency download and resolution from Maven repositories

tools.deps.alpha aims to provide a functional API to access these capabilities.

tools.deps.alpha makes it simple and easy to interactively consume JVM libraries, without dragging in unrelated concerns of building programs or project management. (It should also be a useful shared resource for future work on project and build tools.) 

tools.deps.alpha will support package installers for Clojure (e.g. brew, apt-get, etc) to provide a path for Clojure installation and ongoing Clojure development.

The Clojure 1.9 release for the first time requires multiple artifacts to run Clojure (clojure, spec.alpha, and core.specs.alpha) and thus the issues of transitive dependency are now immediately in front of a Clojure user in the first minute.

Maven-artifacts-first orientation of current tooling has created great rigidity and friction for dev processes - making it hard to e.g. work with libs not yet building/publishing artifacts (new users!), working on speculative changes w/o artifacts, working on mutual changes across multiple libs, give control to a 3rd party tool to manage shared dependencies, and to directly leverage git which is now widely used as a source of truth for code lifecycles.

# Releases Information

[All Released Versions](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.clojure%22%20AND%20a%3A%22tools.deps.alpha%22)

### Current Release

* not yet released 

# Overview

While tools.deps.alpha will predominantly be used within other tools, it can be used directly as well.

* [API Documentation](https://clojure.github.io/tools.deps.alpha/)

## API

There are two fundamental operations supported by tools.deps.alpha: `resolve-deps` and `make-classpath`.

### `resolve-deps`

Usage: `(resolve-args deps-map resolve-args)`

Args:

* deps-map - a dependency map (as typically found in deps.edn). A map with the following keys:
    * `:deps` - a map of library to coordinate (which may be maven, git, local, etc)
    * `:providers` - a map of artifact provider type to provider configuration
    * `:resolve-args` - a map with the same structure as the resolve-args arg which serves as the basis resolve-args
    * `:aliases` - a map from keyword (the alias) to a resolve-args map
* resolve-args - a map with the following optional keys:
    * `:extra-deps` - a map of library to coordinate to add to initial dependency list
    * `:default-deps` - a map of library to coordinate to use if no coordinate is specified anywhere in expansion.
    * `:override-deps` - a map of library to coordinate to use instead of coordinate found by expansion.

Returns:

* a lib-map - map of library to a resolved dep. Resolved deps have the coordinate, the dependents needing the dep (informational), and a path

### `make-classpath`

Usage: `(make-classpath lib-map overrides)`

Args:

* lib-map - a library map, as returned by resolve-deps
* overrides - a map from library to a path to use for that library

Returns:

* a classpath string

## Data structures

### Library

A library is identified by a symbol whose namespace is the groupId and name is the artifactId. If no namespace is specified, the groupId is the same as the artifactId.

Specs:

```clojure
(s/def ::lib symbol?)
```

Examples: `org.clojure/clojure`, `cheshire`

### Coordinate

A coordinate is a map specifying the provider type (a keyword) and version (a string) that will fulfill a library. Initial coordinate types include: `:mvn`, `:local`, `:git`. In some cases, a coordinate may optionally contain other information, such as a path or provider-specific attributes.

Specs:

```clojure
(s/def ::coord (s/multi-spec coord :type))
(s/def ::type keyword?)
(s/def ::path string?)

(defmulti coord :type)
(defmethod coord :mvn [_] (s/keys :opt-un [::version ::path]))
(defmethod coord :local [_] (s/keys :req-un [::path]))
;; :git impl TBD
```

Examples: `{:type :mvn :version "1.2.3"}`, `{:type :local :path "/Users/me/clojure/target/classes"}`

### Providers

A provider is a system that can traverse dependencies and download artifacts.

Specs:

```clojure
;; keys here match the coordinate :type keys
(s/def ::providers (s/keys :opt-un [::mvn ::git]))

(s/def ::url string?)
(s/def ::mvn (s/keys :opt [::repos]))
(s/def ::repo (s/keys :opt-un [::url]))
(s/def ::repo-id string?)
(s/def ::repos (s/map-of ::repo-id ::repo))

;; TODO: more Maven repo info (mirrors, etc)

;; :git impl TBD
```

Example:

```clojure
{:mvn {:repos {"central" {:url "https://repo1.maven.org/maven2/"}
               "clojars" {:url "https://clojars.org/repo/"}}}}
```

### Deps map

A deps map describes a set of dependencies and optional modifications to those dependencies for the purposes of building classpaths.

Specs:

```clojure
(s/def ::deps-map (s/keys :opt-un [::deps ::resolve-args ::providers ::aliases]))

(s/def ::deps (s/map-of ::lib ::coord))

(s/def ::alias unqualified-keyword?)
(s/def ::aliases (s/map-of ::alias map?))
```

### Resolve args map

The resolve args are a set of modifiers that can be used to change the base set of
dependencies.

Semantics:

* extra-deps - additional dependencies to add to the base dependencies, for example, a benchmarking library, a testing library, etc.
* override-deps - alternate coordinates to use instead of the base dependencies. For example, specifying an alternate version of Clojure to use instead of the one included in the deps.
* default-deps - default coordinates to use if a dependency is required, but its coordinate is not. This can be used to provide an external source of dependency information (often handled with managed dependencies in Maven or lein)

Specs:

```clojure
(s/def ::resolve-args (s/keys :opt-un [::extra-deps ::override-deps ::default-deps]))
(s/def ::extra-deps (s/map-of ::lib ::coord))
(s/def ::override-deps (s/map-of ::lib ::coord))
(s/def ::default-deps (s/map-of ::lib ::coord))
```

### Lib map

A lib map represents the result of resolving dependencies. It state the full set of
libraries, where to find the artifact for each library, and which dependents requested
each dependency.

Specs:

```clojure
(s/def ::dependents (s/coll-of ::lib))
(s/def ::resolved-coord (s/merge ::coord (s/keys :req-un [::path] :opt-un [::dependents])))
(s/def ::lib-map (s/map-of ::lib ::resolved-coord)
```

### Classpath overrides

Specs:

```clojure
(s/def ::classpath-overrides (s/map ::lib ::path))
```

### Classpath

Specs:

```clojure
(s/def ::classpath string?)
```

# Developer Information

* [GitHub project](https://github.com/clojure/tools.deps.alpha)

* [How to contribute](http://dev.clojure.org/display/community/Contributing)

* [Bug Tracker](http://dev.clojure.org/jira/browse/TDEPS)

* [Continuous Integration](http://build.clojure.org/job/tools.deps.alpha/)

* [Compatibility Test Matrix](http://build.clojure.org/job/tools.deps.alpha-test-matrix/)



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
