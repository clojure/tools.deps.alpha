(ns clojure.tools.deps.alpha.test-exec
  (:require [clojure.test :refer :all]
            [clojure.tools.deps.alpha.exec :as run]))

(deftest test-parse-args-err
  (is (thrown? clojure.lang.ExceptionInfo (#'run/parse-args [])))
  (is (thrown? clojure.lang.ExceptionInfo (#'run/parse-args ["foo"])))
  (is (thrown? clojure.lang.ExceptionInfo (#'run/parse-args ["-Ff"])))
  (is (thrown? clojure.lang.ExceptionInfo (#'run/parse-args ["-X100"])))
  )

(deftest test-parse-args
  (are [args expected] (= expected (#'run/parse-args args))
    ["-X:foo"] {:alias :foo}
    ["-X:foo" ":bar" "100" "[:baz :qux]" "hi" ":zaz" "\"abc\""] '{:alias :foo :overrides [:bar 100 [:baz :qux] hi :zaz "abc"]}
    ["-Ff/q"] '{:fn f/q}
    ["-Ff/q" "{:arg1 100}"] '{:fn f/q :overrides [{:arg1 100}]}))

(comment
  (run-tests)
  )