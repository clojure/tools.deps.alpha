(ns clojure.tools.deps.alpha.script.test-make-classpath2
  (:require
    [clojure.test :refer [deftest is]]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.script.make-classpath2 :as mc])
  (:import
    [java.io File]))

(def install-data
  {:paths ["src"]
   :deps {'org.clojure/clojure {:mvn/version "1.10.1"}}
   :aliases {:test {:extra-paths ["test"]}}
   :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
               "clojars" {:url "https://repo.clojars.org/"}}})

(defn submap?
  "Is m1 a subset of m2?"
  [m1 m2]
  (if (and (map? m1) (map? m2))
    (every? (fn [[k v]] (and (contains? m2 k)
                          (submap? v (get m2 k))))
      m1)
    (= m1 m2)))

(deftest outside-project
  (let [basis (mc/run-core {:install-deps install-data})]
    (is (submap?
          {:libs {'org.clojure/clojure {:mvn/version "1.10.1"}
                  'org.clojure/spec.alpha {:mvn/version "0.2.176"}
                  'org.clojure/core.specs.alpha {:mvn/version "0.2.44"}}}
          basis))))

(deftest in-project
  (let [basis (mc/run-core {:install-deps install-data
                             :project-deps {:deps {'org.clojure/clojure {:mvn/version "1.9.0"}}}})]
    (is (submap?
          {:libs {'org.clojure/clojure {:mvn/version "1.9.0"}
                  'org.clojure/spec.alpha {:mvn/version "0.1.143"}
                  'org.clojure/core.specs.alpha {:mvn/version "0.1.24"}}}
          basis))))

;; alias  :e with :extra-deps extends the project deps
(deftest extra-deps
  (let [basis (mc/run-core {:install-deps install-data
                             :project-deps {:deps {'org.clojure/clojure {:mvn/version "1.9.0"}}
                                            :aliases {:e {:extra-deps {'org.clojure/test.check {:mvn/version "0.9.0"}}}}}
                             :aliases [:e]})]
    (is (submap?
          {:resolve-args {:extra-deps {'org.clojure/test.check {:mvn/version "0.9.0"}}}
           :libs {'org.clojure/clojure {:mvn/version "1.9.0"}
                  'org.clojure/spec.alpha {:mvn/version "0.1.143"}
                  'org.clojure/core.specs.alpha {:mvn/version "0.1.24"}
                  'org.clojure/test.check {:mvn/version "0.9.0"}}}
          basis))))

;; alias :e with :deps replaces the project deps
(deftest tool-deps
  (let [basis (mc/run-core {:install-deps install-data
                             :project-deps {:deps {'org.clojure/clojure {:mvn/version "1.9.0"}}
                                            :aliases {:t {:deps {'org.clojure/test.check {:mvn/version "0.9.0"}}}}}
                             :aliases [:t]})]
    (is (submap?
          {:paths ["src"]
           :deps {'org.clojure/test.check {:mvn/version "0.9.0"}}
           :libs {'org.clojure/clojure {:mvn/version "1.10.1"}
                  'org.clojure/spec.alpha {:mvn/version "0.2.176"}
                  'org.clojure/core.specs.alpha {:mvn/version "0.2.44"}
                  'org.clojure/test.check {:mvn/version "0.9.0"}}}
          basis))))

;; alias :o with :override-deps overrides the version to use
(deftest override-deps
  (let [basis (mc/run-core {:install-deps install-data
                             :project-deps {:aliases {:o {:override-deps {'org.clojure/clojure {:mvn/version "1.6.0"}}}}}
                             :aliases [:o]})]
    (is (submap?
          {:resolve-args {:override-deps {'org.clojure/clojure {:mvn/version "1.6.0"}}}
           :libs {'org.clojure/clojure {:mvn/version "1.6.0"}}}
          basis))))

(defn select-cp
  [classpath key]
  (->> classpath (filter #(contains? (val %) key)) (apply conj {})))

;; paths and deps in alias replace
(deftest alias-paths-and-deps
  (let [basis (mc/run-core {:install-deps install-data
                             :project-deps {:paths ["a" "b"]
                                            :aliases {:q {:paths ["a" "c"]
                                                          :deps {'org.clojure/clojure {:mvn/version "1.6.0"}}}}}
                             :aliases [:q]})]
    (is (submap?
          {:paths ["a" "c"]
           :deps {'org.clojure/clojure {:mvn/version "1.6.0"}}}
          basis))
    (is (= #{"a" "c"} (-> basis :classpath (select-cp :path-key) keys set)))))

;; paths replace in chain
(deftest paths-replace
  (let [basis (mc/run-core {:install-deps install-data
                             :user-deps {:paths ["x"]}
                             :project-deps {:paths ["y"]}
                             :config-data {:paths ["z"]}})]
    (is (submap? {:paths ["z"]} basis))
    (is (= #{"z"} (-> basis :classpath (select-cp :path-key) keys set)))))

;; :paths in alias replaces, multiple alias :paths will be combined
(deftest alias-paths-replace
  (let [basis (mc/run-core {:install-deps install-data
                             :user-deps {:aliases {:p {:paths ["x" "y"]}}}
                             :project-deps {:aliases {:q {:paths ["z"]}}}
                             :aliases [:p :q]})]
    (is (submap? {:paths ["x" "y" "z"]} basis))
    (is (= #{"x" "y" "z"} (-> basis :classpath (select-cp :path-key) keys set)))))

;; :extra-paths add
(deftest extra-paths-add
  (let [basis (mc/run-core {:install-deps install-data
                             :user-deps {:aliases {:p {:extra-paths ["x" "y"]}}}
                             :project-deps {:aliases {:q {:extra-paths ["z"]}}}
                             :aliases [:p :q]})]
    (is (submap?
          {:paths ["src"]
           :classpath-args {:extra-paths ["x" "y" "z"]}}
          basis))
    (is (= #{"src" "x" "y" "z"} (-> basis :classpath (select-cp :path-key) keys set)))))

;; java opts in aliases are additive
(deftest jvm-opts-add
  (let [basis (mc/run-core {:install-deps install-data
                             :user-deps {:aliases {:j1 {:jvm-opts ["-server" "-Xms100m"]}}}
                             :project-deps {:aliases {:j2 {:jvm-opts ["-Xmx200m"]}}}
                             :aliases [:j1 :j2]})]
    (is (= ["-server" "-Xms100m" "-Xmx200m"] (:jvm basis)))))

;;; main opts replace
(deftest main-opts-replace
  (let [basis (mc/run-core {:install-deps install-data
                             :user-deps {:aliases {:m1 {:main-opts ["a" "b"]}}}
                             :project-deps {:aliases {:m2 {:main-opts ["c"]}}}
                             :aliases [:m1 :m2]})]
    (is (= ["c"] (:main basis)))))

;; repositories should be retained for generate-manifest2's use
(deftest repo-config-retained
  (let [basis (mc/run-core {:install-deps install-data})] ;; install-data has central and clojars
    (is (= #{"central" "clojars"} (-> basis :mvn/repos keys set)))))

;; skip-cp flag prevents resolve-deps/make-classpath
(deftest skip-cp-flag
  (let [basis (mc/run-core {:install-deps install-data
                             :project-deps {:deps {'org.clojure/clojure {:mvn/version "1.10.0"}}}
                             :skip-cp true})]
    (is (nil? (:cp basis)))
    (is (nil? (:libs basis)))))

(comment
  (clojure.test/run-tests)
)
