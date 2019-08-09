;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.util.session
  "Maintains session resources during or across runs of the resolver")

(def ^:dynamic *session* nil)

(defn retrieve
  "Read the current value of key from the session. If absent, and if-absent-fn
  is supplied, invoke the fn, set it in the session (if there is one),
  and return the value."
  ([key]
   (get *session* key))
  ([key if-absent-fn]
   (or
     (get *session* key)
     (let [val (if-absent-fn)]
       (when *session* (set! *session* (assoc *session* key val)))
       val))))

(defmacro with-session
  "Create a new empty session and execute the body"
  [& body]
  `(binding [*session* {}]
     ~@body))
