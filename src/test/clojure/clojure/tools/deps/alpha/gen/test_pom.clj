(ns clojure.tools.deps.alpha.gen.test-pom
  (:require
    [clojure.test :refer [deftest is]]
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.gen.pom :as gen-pom]
    [clojure.tools.deps.alpha :as deps])
  (:import
    [java.io File]))

;; simple check that pom gen is working - gen a pom.xml from tda's deps.edn
(deftest test-pom-gen
  (let [temp-dir (.getParentFile (File/createTempFile "dummy" nil))
        pom (jio/file temp-dir "pom.xml")
        {:keys [root-edn user-edn project-edn]} (deps/find-edn-maps)
        master-edn (deps/merge-edns [root-edn user-edn project-edn])]
    (.delete pom)
    (gen-pom/sync-pom master-edn temp-dir)
    (is (.exists pom))
    (is (not (str/blank? (slurp pom))))))

;; check that optional deps are marked optional
(deftest test-optional
  (let [temp-dir (.getParentFile (File/createTempFile "dummy" nil))
        pom (jio/file temp-dir "pom.xml")]
    (.delete pom)
    (gen-pom/sync-pom
      '{:deps {org.clojure/core.async {:mvn/version "1.1.587" :optional true}}}
      temp-dir)
    (is (.exists pom))
    (let [generated (slurp pom)]
      (is (str/includes? generated "core.async"))
      (is (str/includes? generated "<optional>true</optional>")))))

(deftest test-add-src-dir
  (let [temp-dir (.getParentFile (File/createTempFile "dummy" nil))
        pom (jio/file temp-dir "pom.xml")]
    (.delete pom)
    (spit pom "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n  <modelVersion>4.0.0</modelVersion>\n  <groupId>foo</groupId>\n  <artifactId>foo</artifactId>\n  <version>0.1.0</version>\n  <name>foo</name>\n</project>")
    (gen-pom/sync-pom (deps/root-deps) temp-dir)
    (let [new-pom (slurp pom)]
      (is (str/includes? new-pom "<build>"))
      (is (str/includes? new-pom "<sourceDirectory>src</sourceDirectory>")))))
