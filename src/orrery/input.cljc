(ns orrery.input
  "Reads what the learner types, in either of two spellings:

  - the native format, which the EDN reader reads, with ratios exact
    on every runtime (`bendix.num`);
  - the notation, which `orrery.parse` reads.

  Text that starts with a vector or a keyword is native. Anything
  else is notation. A bare number or ?x reads the same either way.

  The input is a term, a pattern, or rules. Rules are written as
  `[[name lhs rhs] ...]` or as one name: pattern -> replacement per
  line.

  Each reader returns `{:term t}`, `{:pattern p}`, `{:rules [...]}`
  or `{:error message}`."
  (:require [bendix.num :as num]
            [cromulent.pattern :as pat]
            [cromulent.rewrite :as rw]
            [orrery.parse :as parse]))

(defn native?
  "Returns true if `text` is in the native format."
  [text]
  (boolean (re-find #"^\s*[\[:]" text)))

(defn- read-edn [text]
  (try {:value (num/read-string text)}
       (catch #?(:clj Exception :default :default) e
         {:error (str "could not read that: " (ex-message e))})))

(defn- read-text
  "Reads `text` with the reader for its spelling. Returns
  `{:value v}` or `{:error message}`."
  [text]
  (if (native? text) (read-edn text) (parse/term text)))

(defn leaf-count
  "Returns the number of leaves of the term `t`."
  [t]
  (if (vector? t) (reduce + 0 (map leaf-count (rest t))) 1))

(defn size
  "Returns the number of nodes in the tree of the term `t`."
  [t]
  (count (tree-seq vector? rest t)))

(def leaf-limit
  "The largest number of leaves that a term may have if the page is
  to saturate it."
  10)

(defn term-problem
  "Returns nil when `t` is a term, or a message that says why it is
  not one. A term is a keyword, an exact number, or a tagged vector
  with a keyword operator whose children are terms."
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
  "Reads `text` as a term. Returns `{:term t}` or
  `{:error message}`."
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
  "Reads `text` as a pattern, which is a term that may hold
  ?variables. Returns `{:pattern p}` or `{:error message}`."
  [text]
  (let [{:keys [value error]} (read-text text)]
    (cond error {:error error}
          (nil? value) {:error "type a pattern, such as ?x/2 or [:/ ?x 2]"}
          :else (if-let [p (pattern-problem value)] {:error p} {:pattern value}))))

(defn read-rules
  "Reads `text` as rules. The rules are written as
  `[[\"name\" lhs rhs] ...]` or as name: pattern -> replacement
  lines.

  Returns `{:rules data}` or `{:error message}`. `data` is in the
  vector shape and is validated: every variable on the right-hand
  side is bound on the left."
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
