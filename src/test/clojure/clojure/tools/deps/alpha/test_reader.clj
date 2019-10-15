(ns clojure.tools.deps.alpha.test-reader
  (:require
    [clojure.test :refer [deftest is are]]
    [clojure.tools.deps.alpha.reader :as reader]))

(deftest test-merge-or-replace
  (are [vals ret]
    (= ret (apply #'reader/merge-or-replace vals))

    [nil nil] nil
    [nil {:a 1}] {:a 1}
    [{:a 1} nil] {:a 1}
    [{:a 1 :b 1} {:a 2 :c 3} {:c 4 :d 5}] {:a 2 :b 1 :c 4 :d 5}
    [nil 1] 1
    [1 nil] 1
    [1 2] 2))

(deftest test-merge-deps
  (is (= (reader/merge-deps
           [{:deps {'a {:v 1}, 'b {:v 1}}
             :a/x {:a 1}
             :a/y "abc"}
            {:deps {'b {:v 2}, 'c {:v 3}}
             :a/x {:b 1}
             :a/y "def"}
            nil])
        {:deps {'a {:v 1}, 'b {:v 2}, 'c {:v 3}}
         :a/x {:a 1, :b 1}
         :a/y "def"})))