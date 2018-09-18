# API

There are two fundamental operations supported by tools.deps.alpha: `resolve-deps` and `make-classpath`.

### `resolve-deps`

Usage: `(resolve-deps deps-map resolve-args)`

Args:

* deps-map - a dependency map (as typically found in deps.edn). A map with the following keys:
    * `:deps` - a map of library to coordinate (which may be maven, git, local, etc)
    * `:paths` - a vector of string paths which are project-specific classpath roots
    * `:aliases` - a map from keyword (the alias) to a resolve-args map
    * `<providers>` - provider-specific attributes with qualified key names
* resolve-args - a map with the following optional keys:
    * `:extra-deps` - a map of library to coordinate to add to initial dependency list
    * `:default-deps` - a map of library to coordinate to use if no coordinate is specified anywhere in expansion.
    * `:override-deps` - a map of library to coordinate to use instead of coordinate found by expansion.

Returns:

* a lib-map - map of library to a resolved dep. Resolved deps have the coordinate, the dependents needing the dep (informational), and a path

### `make-classpath`

Usage: `(make-classpath lib-map paths classpath-args)`

Args:

* lib-map - a library map, as returned by resolve-deps
* paths - a vector of string paths
* classpath-args - a map with the following optional keys:
    * `:classpath-overrides` - a map from library to a path to use for that library
    * `:extra-paths` - a vector of extra string paths within this project

Returns:

* a classpath string

## Example usage

```clojure
(require '[clojure.tools.deps.alpha :as deps])

(->
  '{:deps {org.clojure/clojure {:mvn/version "1.9.0"}
           org.clojure/core.async {:mvn/version "0.4.474"}}
    :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                "clojars" {:url "https://repo.clojars.org/"}}}
  (deps/resolve-deps nil)
  (deps/make-classpath nil nil))
```

