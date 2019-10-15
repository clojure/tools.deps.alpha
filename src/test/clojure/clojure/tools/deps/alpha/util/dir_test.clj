;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.util.dir-test
  (:require
    [clojure.tools.deps.alpha.util.dir :as dir]
    [clojure.test :refer [deftest is]])
  (:import
    [java.io File]))

(deftest test-as-canonical
  (let [base (.getCanonicalFile (File. "."))]
    (binding [dir/*the-dir* base]
      (let [abs (File. "/a/b")]
        (is (= abs (dir/as-canonical abs)))
        (is (= (File. base "xyz") (dir/as-canonical (File. "xyz"))))))))