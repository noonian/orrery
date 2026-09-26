(ns orrery.input
  "Learner input, in either spelling: the native format, read by the
  EDN reader with ratios exact on every runtime (bendix.num), or the
  notation, read by orrery.parse. Text that starts with a vector or a
  keyword is native; anything else is notation, and a bare number or
  ?x reads the same either way. A term, a pattern, or rules as
  [[name lhs rhs] ...] or one name: pattern -> replacement per line.
  Each reader returns {:term t}, {:pattern p}, {:rules [...]} or
  {:error message}."
  (:require [bendix.num :as num]
            [cromulent.pattern :as pat]
            [cromulent.rewrite :as rw]
            [orrery.parse :as parse]))

(defn native?
  "Is text in the native format?"
  [text]
  (boolean (re-find #"^\s*[\[:]" text)))

(defn- read-edn [text]
  (try {:value (num/read-string text)}
       (catch #?(:clj Exception :default :default) e
         {:error (str "could not read that: " (ex-message e))})))

(defn- read-text
  "{:value v} or {:error message}, by the spelling of text."
  [text]
  (if (native? text) (read-edn text) (parse/term text)))

(defn leaf-count
  "How many leaves t has."
  [t]
  (if (vector? t) (reduce + 0 (map leaf-count (rest t))) 1))

(defn size
  "How many nodes the tree of t has."
  [t]
  (count (tree-seq vector? rest t)))

(def leaf-limit
  "The most leaves a term the page saturates may have."
  10)

(defn term-problem
  "nil, or why t is not a term: a tagged vector with a keyword
  operator and keyword or number leaves."
  [t]
  (cond
    (vector? t) (cond (empty? t) "an empty vector is not a term"
                      (not (keyword? (first t))) (str "an operator is a keyword, not " (pr-str (first t)))
                      :else (some term-problem (rest t)))
    (keyword? t) nil
    (num/rational? t) nil
    (number? t) (str (pr-str t) " is not exact; write a ratio such as 1/2")
    (pat/variable? t) (str t " is a pattern variable; a term has none")
    :else (str (pr-str t) " is not a variable or a number")))

(defn read-term
  "{:term t} or {:error message}."
  [text]
  (let [{:keys [value error]} (read-text text)]
    (cond error {:error error}
          (nil? value) {:error "type a term, such as 2·x + y or [:+ [:* 2 :x] :y]"}
          :else (if-let [p (term-problem value)] {:error p} {:term value}))))

(defn- pattern-problem [p]
  (cond
    (vector? p) (cond (empty? p) "an empty vector is not a pattern"
                      (not (keyword? (first p))) (str "an operator is a keyword, not " (pr-str (first p)))
                      :else (some pattern-problem (rest p)))
    (or (keyword? p) (num/rational? p) (pat/variable? p)) nil
    (number? p) (str (pr-str p) " is not exact; write a ratio such as 1/2")
    :else (str (pr-str p) " is not a pattern variable (?x), a keyword or a number")))

(defn read-pattern
  "{:pattern p} or {:error message}: a term that may hold ?variables."
  [text]
  (let [{:keys [value error]} (read-text text)]
    (cond error {:error error}
          (nil? value) {:error "type a pattern, such as ?x/2 or [:/ ?x 2]"}
          :else (if-let [p (pattern-problem value)] {:error p} {:pattern value}))))

(defn read-rules
  "Rules as [[\"name\" lhs rhs] ...] or as name: pattern -> replacement
  lines: {:rules data} in the vector shape, validated (every
  right-hand variable bound on the left), or {:error message}."
  [text]
  (let [{:keys [value error]} (if (native? text) (read-edn text) (parse/rules text))]
    (cond
      error {:error error}
      (not (and (vector? value) (seq value) (every? vector? value)))
      {:error "rules are one per line, name: pattern -> replacement, or a vector of [\"name\" lhs rhs] vectors"}
      (not= (count value) (count (distinct (map first value))))
      {:error "rule names must be distinct"}
      :else
      (let [problems (keep (fn [[n lhs rhs :as r]]
                             (cond (not= 3 (count r)) (str "a rule is [\"name\" lhs rhs]: " (pr-str r))
                                   (not (string? n)) (str "a rule's name is a string: " (pr-str n))
                                   :else (or (pattern-problem lhs) (pattern-problem rhs))))
                           value)]
        (if (seq problems)
          {:error (first problems)}
          (try (doseq [[n lhs rhs] value] (rw/rule n lhs rhs))
               {:rules value}
               (catch #?(:clj Exception :default :default) e
                 {:error (str (ex-message e)
                              (when-let [u (:unbound (ex-data e))] (str ": " (pr-str u))))})))))))
