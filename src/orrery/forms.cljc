(ns orrery.forms
  "Reads the REPL's text for its shape, without evaluating it. It
  finds where each top-level form starts and ends, says whether the
  text leaves a form open, and says how far to indent a new line.

  The reader knows as much of Clojure's syntax as the shape needs:
  brackets, strings, comments, character literals such as `\\(`, and
  the prefixes that attach to the next form, such as `'`, `#{`, `#(`,
  `#_` and `^`. It reads no values, so text that Clojure would refuse
  still has a shape here.

  A position is an index into the text. A form is a map of `:start`,
  the position of its first character, and `:end`, the position after
  its last."
  (:require [clojure.string :as str]))

(defn- whitespace? [c] (or (= c \,) (= c \space) (= c \tab) (= c \newline) (= c \return)))

(defn- opener? [c] (or (= c \() (= c \[) (= c \{)))

(defn- closer? [c] (or (= c \)) (= c \]) (= c \})))

(defn- delimits? [c]
  (or (whitespace? c) (opener? c) (closer? c) (= c \;) (= c \")))

(defn- alphanumeric? [c]
  (boolean (re-matches #"[A-Za-z0-9]" (str c))))

(defn- token-end
  "Returns the position after the token that starts at `i`."
  [s i]
  (let [n (count s)]
    (loop [j (inc i)]
      (if (and (< j n) (not (delimits? (nth s j)))) (recur (inc j)) j))))

(defn- string-end
  "Returns the position after the string whose opening quote is at
  `i`, or nil when the text ends inside the string."
  [s i]
  (let [n (count s)]
    (loop [j (inc i)]
      (cond (>= j n) nil
            (= \\ (nth s j)) (recur (+ j 2))
            (= \" (nth s j)) (inc j)
            :else (recur (inc j))))))

(defn- char-end
  "Returns the position after the character literal whose backslash
  is at `i`. The literal is the character after the backslash and any
  letters or digits after that, so `\\newline` and `\\u00e9` are one
  literal each and `\\(` is one too."
  [s i]
  (let [n (count s)]
    (if (>= (inc i) n)
      n
      (loop [j (+ i 2)]
        (if (and (< j n) (alphanumeric? (nth s j))) (recur (inc j)) j)))))

(defn- line-end [s i]
  (let [j (.indexOf ^String s "\n" (int i))]
    (if (neg? j) (count s) j)))

;; ---------------------------------------------------------------------------
;; the scan
;;
;; The scan keeps a stack of levels. The first is the top level of the
;; text, and each open bracket adds one. A level knows the element it
;; is reading: where it started and how many forms it still needs. A
;; prefix makes an element need more forms: `'` one, `^` two (the
;; metadata and what it is put on). A level also keeps the elements
;; it has begun, and the top level keeps the forms it has finished.

(def ^:private level {:start nil :need 0 :elems [] :done []})

(defn- update-top [levels f & args]
  (apply update levels (dec (count levels)) f args))

(defn- begin
  "Begins an element at `pos` in the innermost level, unless that
  level is already reading one. `token` is the text of the element
  when it is a token."
  [levels pos token]
  (update-top levels (fn [l]
                       (if (pos? (:need l))
                         l
                         (-> l
                             (assoc :start pos :need 1)
                             (update :elems conj {:pos pos :token token}))))))

(defn- prefix
  "Reads a prefix at `pos` that needs `k` forms after it."
  [levels pos k]
  (-> (begin levels pos nil)
      (update-top update :need #(+ (dec %) k))))

(defn- finish
  "Finishes a form that ends at `end` in the innermost level. The
  element is done when it needs no more forms."
  [levels end]
  (update-top levels (fn [{:keys [need start] :as l}]
                       (if (> need 1)
                         (assoc l :need (dec need))
                         (-> l
                             (update :done conj {:start start :end end})
                             (assoc :start nil :need 0))))))

(defn- dispatch-prefix
  "Returns the length of the prefix that starts with the `#` at `i`,
  and how many forms it needs, as [length k]. Returns nil when the
  `#` starts a token of its own, as in `##Inf`."
  [s i]
  (let [n (count s)
        c (when (< (inc i) n) (nth s (inc i)))]
    (cond
      (nil? c) [1 1]
      (or (= c \{) (= c \()) [1 1]
      (= c \?) (if (and (< (+ i 2) n) (= \@ (nth s (+ i 2)))) [3 1] [2 1])
      (or (= c \_) (= c \') (= c \=)) [2 1]
      (= c \#) nil
      ;; a tag, as in #inst "…", or a namespace for a map, as in #:a{…}
      :else [(- (token-end s i) i) 1])))

(defn scan
  "Reads `s` up to the position `limit`, the whole text by default.
  Returns a map:

    - :forms holds the top-level forms that are complete.
    - :open is the top-level form that the text leaves open, from its
      start to the limit, or nil.
    - :stack holds the brackets still open at the limit, the outermost
      first. Each is a map of :char, :pos, and :elems, the elements
      begun inside it, each with its :pos and, for a token, its
      :token.
    - :in is :string when the limit falls inside a string."
  ([s] (scan s (count s)))
  ([s limit]
   (let [s (subs s 0 limit)
         n (count s)]
     (loop [i 0 levels [level] in nil]
       (if (>= i n)
         (let [top (first levels)]
           {:forms (:done top)
            :open (when (:start top) {:start (:start top) :end n})
            :stack (mapv #(select-keys % [:char :pos :elems]) (rest levels))
            :in in})
         (let [c (nth s i)]
           (cond
             (whitespace? c) (recur (inc i) levels nil)
             (= c \;) (recur (line-end s i) levels nil)
             (= c \") (if-let [j (string-end s i)]
                        (recur j (-> levels (begin i nil) (finish j)) nil)
                        (recur n (begin levels i nil) :string))
             (= c \\) (let [j (char-end s i)]
                        (recur j (-> levels (begin i nil) (finish j)) nil))
             (opener? c) (recur (inc i) (-> levels (begin i nil) (conj (assoc level :char c :pos i))) nil)
             (closer? c) (if (> (count levels) 1)
                           (recur (inc i) (-> levels pop (finish (inc i))) nil)
                           (recur (inc i) (-> levels (begin i nil) (finish (inc i))) nil))
             (or (= c \') (= c \`) (= c \@)) (recur (inc i) (prefix levels i 1) nil)
             (= c \~) (let [k (if (and (< (inc i) n) (= \@ (nth s (inc i)))) 2 1)]
                        (recur (+ i k) (prefix levels i 1) nil))
             (= c \^) (recur (inc i) (prefix levels i 2) nil)
             (and (= c \#) (dispatch-prefix s i))
             (let [[k need] (dispatch-prefix s i)]
               ;; #" starts a regular expression, which reads as a string
               (if (and (< (inc i) n) (= \" (nth s (inc i))))
                 (if-let [j (string-end s (inc i))]
                   (recur j (-> levels (begin i nil) (finish j)) nil)
                   (recur n (begin levels i nil) :string))
                 (recur (+ i k) (prefix levels i need) nil)))
             :else (let [j (token-end s i)]
                     (recur j (-> levels (begin i (subs s i j)) (finish j)) nil)))))))))

;; ---------------------------------------------------------------------------
;; forms

(defn- discarded? [s {:keys [start]}]
  (= "#_" (subs s start (min (count s) (+ start 2)))))

(defn top-level
  "Returns the top-level forms of `s` in order, each as a map of
  :start and :end. A form that the text leaves open is last, and it
  has :open? true. A form discarded with `#_` is left out."
  [s]
  (let [{:keys [forms open]} (scan s)]
    (into [] (remove #(discarded? s %))
          (cond-> forms open (conj (assoc open :open? true))))))

(defn complete?
  "Returns true when `s` leaves no bracket, string or prefix open.
  Text with no forms in it is complete."
  [s]
  (nil? (:open (scan s))))

(defn form-at
  "Returns the top-level form of `s` at the position `pos`. That is
  the form that holds `pos`, where a caret just after the last
  character counts as in the form. When no form holds `pos`, it is
  the last form before `pos`, or else the first form after it.
  Returns nil when `s` has no forms."
  [s pos]
  (let [fs (top-level s)]
    (or (some #(when (<= (:start %) pos (:end %)) %) fs)
        (last (filter #(<= (:end %) pos) fs))
        (first fs))))

(defn text-of
  "Returns the text of the form `f` in `s`."
  [s f]
  (subs s (:start f) (:end f)))

;; ---------------------------------------------------------------------------
;; indentation

(defn- column
  "Returns the column of the position `pos` in `s`, counted from 0."
  [s pos]
  (- pos (inc (.lastIndexOf ^String s "\n" (int (dec pos))))))

(defn- same-line? [s a b]
  (not (str/includes? (subs s a b) "\n")))

(def ^:private body-heads
  "The heads whose forms indent their body by two spaces, as a block.
  A head whose name starts with def or with- does too."
  #{"fn" "fn*" "let" "letfn" "loop" "binding" "if" "if-not" "if-let" "if-some"
    "when" "when-not" "when-let" "when-some" "when-first" "doseq" "dotimes" "for"
    "do" "try" "catch" "finally" "ns" "case" "cond" "condp" "cond->" "cond->>"
    "comment" "reify" "proxy" "extend-protocol" "extend-type" "testing" "future"
    "locking" "doto"})

(defn- symbol-name
  "Returns the name of the token `t` when it spells a symbol, without
  its namespace. Returns nil for a number, a keyword or a literal."
  [t]
  (when (and t (re-matches #"[^0-9:\\\"#'].*" t))
    (let [i (.lastIndexOf ^String t "/")]
      (if (and (pos? i) (< i (dec (count t)))) (subs t (inc i)) t))))

(defn- body-head? [t]
  (when-let [nm (symbol-name t)]
    (or (contains? body-heads nm)
        (str/starts-with? nm "def")
        (str/starts-with? nm "with-"))))

(defn indent
  "Returns the column at which a new line starts when a line break is
  put into `s` at the position `pos`. Only the text before `pos`
  counts.

  - Outside every bracket, the line starts at column 0.
  - Inside a vector, a map or a set, it lines up under the first
    element, one column in from the bracket.
  - Inside a list headed by a form such as `let`, `defn` or `when`,
    it is indented two columns from the bracket.
  - Inside any other list headed by a symbol or a keyword, it lines
    up under the first argument when that argument is on the line of
    the bracket, and under the head otherwise.
  - Inside a string, the line starts at column 0."
  [s pos]
  (let [{:keys [stack in]} (scan s pos)
        s (subs s 0 pos)]
    (if (or (= :string in) (empty? stack))
      0
      (let [{:keys [char pos elems]} (peek stack)
            col (column s pos)
            [head arg] elems]
        (cond
          (not= \( char) (inc col)
          (nil? head) (inc col)
          (body-head? (:token head)) (+ col 2)
          (and arg (or (symbol-name (:token head)) (str/starts-with? (str (:token head)) ":"))
               (same-line? s pos (:pos arg)))
          (column s (:pos arg))
          :else (inc col))))))
