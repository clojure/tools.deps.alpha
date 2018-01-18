(ns clojure.tools.deps.test-alpha
  (:require
    [clojure.test :refer :all]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.extensions.faken :as fkn]))

(def repo
  ;; "real"
  {'org.clojure/clojure {{:fkn/version "1.9.0"}
                         [['org.clojure/spec.alpha {:fkn/version "0.1.124"}]
                          ['org.clojure/core.specs.alpha {:fkn/version "0.1.10"}]]}
   'org.clojure/spec.alpha {{:fkn/version "0.1.124"} nil
                            {:fkn/version "0.1.1"} nil}
   'org.clojure/core.specs.alpha {{:fkn/version "0.1.10"} nil}

   ;; testing various scenarios
   'e1/a {{:fkn/version "1"} [['e1/b {:fkn/version "1"}]
                             ['e1/c {:fkn/version "2"}]]}
   'e1/b {{:fkn/version "1"} [['e1/c {:fkn/version "1"}]]}
   'e1/c {{:fkn/version "1"} nil
         {:fkn/version "2"} nil}})

(deftest test-basic-expand
  (fkn/with-libs repo
    (is (= (set (keys (deps/resolve-deps {:deps {'org.clojure/clojure {:fkn/version "1.9.0"}}} nil)))
          #{'org.clojure/clojure 'org.clojure/spec.alpha 'org.clojure/core.specs.alpha}))))

(deftest test-top-dominates
  (fkn/with-libs repo
    ;; dependent dep decides version
    (is (= (-> {:deps {'org.clojure/clojure {:fkn/version "1.9.0"}}}
             (deps/resolve-deps nil)
             (get 'org.clojure/spec.alpha)
             :fkn/version)
          "0.1.124"))

    ;; top dep wins
    (is (= (-> {:deps {'org.clojure/clojure {:fkn/version "1.9.0"}
                       'org.clojure/spec.alpha {:fkn/version "0.1.1"}}}
             (deps/resolve-deps nil)
             (get 'org.clojure/spec.alpha)
             :fkn/version)
          "0.1.1"))))

(deftest test-override-deps
  (fkn/with-libs repo
    ;; override dep wins
    (is (= (-> {:deps {'org.clojure/clojure {:fkn/version "1.9.0"}}}
             (deps/resolve-deps
               {:override-deps {'org.clojure/spec.alpha {:fkn/version "0.1.1"}}})
             (get 'org.clojure/spec.alpha)
             :fkn/version)
          "0.1.1"))))

;; +a1 -> +b1 -> -c1
;;     -> +c2
(deftest test-dep-choice
  (fkn/with-libs repo
    (= (->> (deps/resolve-deps {:deps {'e1/a {:fkn/version "1"}}} nil)
         (reduce-kv #(assoc %1 (-> %2 name keyword) (:fkn/version %3)) {}))
      {:a 1, :b 1, :c 2})))

;; -> +a1 -> +d1
;; -> +b1 -> -e1 -> -d2
;; -> +c1 -> +e2
(deftest test-dep-choice-cascade
  (fkn/with-libs
    {'ex/a {{:fkn/version "1"} [['ex/d {:fkn/version "1"}]]}
     'ex/b {{:fkn/version "1"} [['ex/e {:fkn/version "1"}]]}
     'ex/c {{:fkn/version "1"} [['ex/e {:fkn/version "2"}]]}
     'ex/d {{:fkn/version "1"} nil
            {:fkn/version "2"} nil}
     'ex/e {{:fkn/version "1"} [['ex/d {:fkn/version "2"}]]
            {:fkn/version "2"} nil}}
    (let [r (->> (deps/resolve-deps {:deps {'ex/a {:fkn/version "1"}
                                            'ex/b {:fkn/version "1"}
                                            'ex/c {:fkn/version "1"}}} nil)
              (reduce-kv #(assoc %1 (-> %2 name keyword) (:fkn/version %3)) {}))]
      (println "\n" r)
      (is (= r {:a "1", :b "1", :c "1", :d "1", :e "2"})))))

(test-dep-choice-cascade)