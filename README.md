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

* [Getting Started](https://clojure.org/guides/getting_started)
* [Deps and CLI Guide](https://clojure.org/guides/deps_and_cli)
* [Deps and CLI Reference](https://clojure.org/reference/deps_and_cli)
* [Dependency Heaven](http://cdn.cognitect.com/presentations/2017/dependency_heaven.pdf) from EuroClojure 2017 - [video](https://youtube.com/watch?v=sStlTye-Kjk)
* [Projects that use or work with tools.deps and clj](https://github.com/clojure/tools.deps.alpha/wiki/Tools)

# Release Information

Latest release: 0.7.541

* [All released versions](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.clojure%22%20AND%20a%3A%22tools.deps.alpha%22)

[deps.edn](https://clojure.org/guides/deps_and_cli) dependency information:

```
org.clojure/tools.deps.alpha {:mvn/version "0.7.541"}
```

[Leiningen](https://github.com/technomancy/leiningen/) dependency information:

```
[org.clojure/tools.deps.alpha "0.7.541"]
```

[Maven](https://maven.apache.org) dependency information:

```
<dependency>
  <groupId>org.clojure</groupId>
  <artifactId>tools.deps.alpha</artifactId>
  <version>0.7.541</version>
</dependency>
```

# API 

For info on using tools.deps as a library, see:

* [API Usage](API.md)

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
