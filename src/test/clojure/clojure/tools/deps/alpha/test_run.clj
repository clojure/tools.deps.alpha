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

(deftest test-deep-merge
  (are [a b expected] (= expected (#'run/deep-merge a b))
    nil nil nil
    1 2 2
    {} {} {}
    {:a 1} {:b 2} {:a 1 :b 2}
    {:a 1} {:a 2} {:a 2}
    {:a 1} 2 2
    {:a {:b 1}} {:a {:b 2}} {:a {:b 2}}
    {:a {:b 2} :x 1} {:a {:c 3} :y 2} {:a {:b 2 :c 3} :x 1 :y 2}
    {:a 1} nil {:a 1}
    nil {:a 1} {:a 1}))