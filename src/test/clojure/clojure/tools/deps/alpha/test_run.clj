(ns clojure.tools.deps.alpha.test-run
  (:require [clojure.test :refer :all]
            [clojure.tools.deps.alpha.run :as run]))

(deftest test-parse-args-err
  (is (thrown? clojure.lang.ExceptionInfo (#'run/parse-args [])))
  (is (thrown? clojure.lang.ExceptionInfo (#'run/parse-args ["foo"])))
  (is (thrown? clojure.lang.ExceptionInfo (#'run/parse-args ["-X:foo" ":bar"])))
  (is (thrown? clojure.lang.ExceptionInfo (#'run/parse-args ["-Xf"])))
  (is (thrown? clojure.lang.ExceptionInfo (#'run/parse-args ["-X100"])))
  )

(deftest test-parse-args
  (are [args expected] (= expected (#'run/parse-args args))
    ["-X:foo"] {:alias :foo}
    ["-X:foo" ":bar" "100" ":baz:qux" "hi" ":zaz" "\"abc\""] '{:alias :foo :args {:bar 100 :baz {:qux hi} :zaz "abc"}}
    ["-Xf/q"] '{:fname f/q}
    ["-Xf/q" ":base"] '{:fname f/q :alias :base}
    ["-Xf/q" ":base" ":arg1" "100"] '{:fname f/q :alias :base :args {:arg1 100}}
    ["-Xf/q" ":arg1" "100"] '{:fname f/q :args {:arg1 100}}))

