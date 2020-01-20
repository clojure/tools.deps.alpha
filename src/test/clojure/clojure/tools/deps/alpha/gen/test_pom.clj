(ns clojure.tools.deps.alpha.gen.test-pom
  (:require
    [clojure.test :refer :all]
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.gen.pom :as gen-pom]
    [clojure.tools.deps.alpha.reader :as reader])
  (:import
    [java.io File]))

;; simple check that pom gen is working - gen a pom.xml from tda's deps.edn
(deftest test-pom-gen
  (let [temp-dir (.getParentFile (File/createTempFile "dummy" nil))
        pom (jio/file temp-dir "pom.xml")]
    (.delete pom)
    (gen-pom/sync-pom
      (reader/read-deps [(jio/file "deps.edn")])
      temp-dir)
    (is (.exists pom))
    (is (not (str/blank? (slurp pom))))))