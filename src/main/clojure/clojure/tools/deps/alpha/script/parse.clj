(ns clojure.tools.deps.alpha.script.parse
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.tools.deps.alpha.reader :as reader])
  (:import
    [java.io File]))

(defn parse-files
  "Parses a string of comma-delimited files into a collection of
  Files, filtering only those that exist."
  [s]
  (->> (str/split s #",")
    (map jio/file)
    (filter #(.exists ^File %))))

(defn parse-kws
  "Parses a concatenated string of keywords into a collection of keywords
  Ex: (parse-kws \":a:b:c\") ;; returns: (:a :b :c)"
  [s]
  (->> (str/split (or s "") #":")
    (remove str/blank?)
    (map
      #(if-let [i (str/index-of % \/)]
         (keyword (subs % 0 i) (subs % (inc i)))
         (keyword %)))))

(defn parse-config
  "Parses a string of edn into a deps map."
  [s]
  (#'reader/canonicalize-all-syms  ;; to be removed in the future
    (edn/read-string s)))