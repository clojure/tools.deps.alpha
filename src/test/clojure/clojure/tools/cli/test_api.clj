(ns clojure.tools.cli.test-api
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.test :refer [deftest is are] :as test]
    [clojure.tools.cli.api :as api]
    [clojure.tools.deps.alpha.util.maven :as mvn]
    [clojure.tools.deps.alpha.util.dir :as dir])
  (:import
    [java.io File]))

(def ^:dynamic ^File *test-dir*)

(defmacro with-test-dir
  [& body]
  `(let [name# (-> test/*testing-vars* last symbol str)
         dir# (jio/file "test-out" name#)]
     (.delete dir#)
     (.mkdirs dir#)
     (binding [*test-dir* dir#]
       ~@body)))

(deftest test-prep-across-modules
  (with-test-dir
    (spit (jio/file *test-dir* "deps.edn")
      (pr-str {:deps {'mono/moda {:local/root "mod/a"}
                      'mono/modb {:local/root "mod/b"}}}))
    (let [adeps (jio/file *test-dir* "mod/a/deps.edn")]
      (jio/make-parents adeps)
      (spit adeps "{}"))
    (let [bdeps (jio/file *test-dir* "mod/b/deps.edn")]
      (jio/make-parents bdeps)
      (spit bdeps
        (pr-str {:paths ["src"]
                 :deps/prep-lib {:alias :resources
                                 :fn 'bcore/generate
                                 :ensure "target/resources"}
                 :aliases {:resources {:deps {'mono/moda {:local/root "../a"}} :paths ["src"]}}})))
    (let [bgen (jio/file *test-dir* "mod/b/src/bcore.clj")
          cp-path (.getCanonicalPath (jio/file *test-dir* "mod/b/target/resources/cp"))]
      (jio/make-parents bgen)
      (spit bgen
        (str
          "(ns bcore)"
          (format "(defn generate [_] (.mkdirs (.getParentFile (java.io.File. \"%s\"))) (spit \"%s\" (System/getProperty \"java.class.path\")))"
            cp-path cp-path)))
      (api/prep
        {:root {:mvn/repos mvn/standard-repos}
         :user nil
         :project {:deps {'org.clojure/clojure {:mvn/version "1.11.1"}
                          'mono/root {:local/root (.getPath *test-dir*)}}}
         ;; :log :debug
         :force true})
      (let [cp-out (slurp cp-path)]
        (is (true? (str/includes? cp-out (.getCanonicalPath (jio/file *test-dir* "mod/a/src")))))))))

(comment
  (test-prep-across-modules)
  )