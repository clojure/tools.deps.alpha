;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.extensions.test-git
  (:require
    [clojure.test :refer [deftest is are]]
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.extensions.git :as git])
  (:import
    [clojure.lang ExceptionInfo]))

(deftest auto-git-url
  (are [url lib] (= url (git/auto-git-url lib))
    "https://github.com/clojure/tools.deps.alpha.git" 'io.github.clojure/tools.deps.alpha
    "https://github.com/clojure/tools.deps.alpha.git" 'com.github.clojure/tools.deps.alpha
    "https://gitlab.com/clojure/tools.deps.alpha.git" 'io.gitlab.clojure/tools.deps.alpha
    "https://gitlab.com/clojure/tools.deps.alpha.git" 'com.gitlab.clojure/tools.deps.alpha
    "https://bitbucket.org/clojure/tools.deps.alpha.git" 'io.bitbucket.clojure/tools.deps.alpha
    "https://bitbucket.org/clojure/tools.deps.alpha.git" 'org.bitbucket.clojure/tools.deps.alpha))

(deftest full-sha
  (is (true? (git/full-sha? "f7443aa3ad854d5ab351f7ea327d6b161c5f3850")))
  (is (false? (git/full-sha? "f7443aa"))))

(deftest canonicalize
  ;; infer url
  (is (= ['io.github.clojure/tools.deps.alpha {:git/url "https://github.com/clojure/tools.deps.alpha.git"
                                               :git/sha "9bf5778dc26dd5018dbf04fc8e7dbb32ddc4036c"}]
        (ext/canonicalize 'io.github.clojure/tools.deps.alpha {:git/sha "9bf5778dc26dd5018dbf04fc8e7dbb32ddc4036c"} {})))

  ;; standardize sha
  (is (= ['io.github.clojure/tools.deps.alpha {:git/url "https://github.com/clojure/tools.deps.alpha.git"
                                               :git/sha "9bf5778dc26dd5018dbf04fc8e7dbb32ddc4036c"}]
        (ext/canonicalize 'io.github.clojure/tools.deps.alpha {:sha "9bf5778dc26dd5018dbf04fc8e7dbb32ddc4036c"} {})))

  ;; standardize sha/tag
  (is (= ['io.github.clojure/tools.deps.alpha {:git/url "https://github.com/clojure/tools.deps.alpha.git"
                                               :git/sha "c2a3bbe9df5f574c8af17f07d18b75198194d26e"
                                               :git/tag "tools.deps.alpha-0.5.317"}]
        (ext/canonicalize 'io.github.clojure/tools.deps.alpha {:sha "c2a3bbe"
                                                               :tag "tools.deps.alpha-0.5.317"} {}))))

(deftest canonicalize-errors
  ;; unknown coord type
  (is (thrown-with-msg? ExceptionInfo #"Coord of unknown type"
        (ext/canonicalize 'io.github.clojure/tools.deps.alpha {} {})))

  ;; both :sha and :git/sha
  (is (thrown-with-msg? ExceptionInfo #"git coord has both :sha and :git/sha for io.github.clojure/tools.deps.alpha"
        (ext/canonicalize 'io.github.clojure/tools.deps.alpha {:sha "9bf5778dc26dd5018dbf04fc8e7dbb32ddc4036c"
                                                               :git/sha "9bf5778dc26dd5018dbf04fc8e7dbb32ddc4036c"} {})))

  ;; lib must be qualified
  (is (thrown-with-msg? ExceptionInfo #"Invalid lib name: tools.deps.alpha"
        (ext/canonicalize 'tools.deps.alpha {:git/url "https://github.com/clojure/tools.deps.alpha.git"} {})))

  ;; require full sha if no tag
  (is (thrown-with-msg? ExceptionInfo #"Library io.github.clojure/tools.deps.alpha has prefix sha, use full sha or add tag"
        (ext/canonicalize 'io.github.clojure/tools.deps.alpha {:git/sha "9bf5778"} {})))

  ;; require at least prefix sha with tag
  (is (thrown-with-msg? ExceptionInfo #"Library io.github.clojure/tools.deps.alpha has coord with missing sha"
        (ext/canonicalize 'io.github.clojure/tools.deps.alpha {:git/tag "tools.deps.alpha-0.5.317"} {})))

  ;; sha/tag must point to same commit
  (is (thrown-with-msg? ExceptionInfo #"Library io.github.clojure/tools.deps.alpha has sha and tag that point to different commits"
        (ext/canonicalize 'io.github.clojure/tools.deps.alpha {:git/tag "tools.deps.alpha-0.5.317" :git/sha "9bf5778"} {})))

  ;; tag must be a tag
  (is (thrown-with-msg? ExceptionInfo #"Library io.github.clojure/tools.deps.alpha has invalid tag"
        (ext/canonicalize 'io.github.clojure/tools.deps.alpha {:git/tag "9bf5778" :git/sha "9bf5778"} {})))
  )

(comment
  (canonicalize)
  )