# API

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
