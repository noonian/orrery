(ns orrery.parse-test
  (:require [bendix.num :as num]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [orrery.generate :as generate]
            [orrery.lessons :as lessons]
            [orrery.notation :as notation]
            [orrery.parse :as parse]))

(defn- reads [text] (:value (parse/term text)))
(defn- problem [text] (:error (parse/term text)))
(def half (num/ratio 1 2))

(deftest precedence-and-association
  (is (= [:+ [:* 2 :x] :y] (reads "2·x + y")))
  (is (= [:+ [:+ :a :b] :c] (reads "a + b + c")) "a chain nests to the left")
  (is (= [:+ :a [:+ :b :c]] (reads "a + (b + c)")))
  (is (= [:- [:- :a :b] :c] (reads "a − b − c")))
  (is (= [:<< :a [:+ 1 :b]] (reads "a << 1 + b")) "shifts bind loosest")
  (is (= [:+ [:<< :a 1] :b] (reads "(a << 1) + b")))
  (is (= [:* [:+ :x 1] [:- :y 2]] (reads "(x + 1)·(y − 2)")))
  (is (= [:/ [:* :a 2] 2] (reads "(a·2)/2")))
  (is (= [:/ :a [:* :b :c]] (reads "a/(b·c)")))
  (is (= [:* [:/ :a :b] :c] (reads "a/b·c")) "quotients and products chain to the left")
  (is (= [:expt :x [:expt :y :z]] (reads "x^y^z")) "powers nest to the right")
  (is (= [:expt [:expt :x :y] :z] (reads "(x^y)^z")))
  (is (= [:expt :x [:+ :n 1]] (reads "x^(n + 1)")))
  (is (= [:* 2 [:expt :x 2]] (reads "2·x²")))
  (is (= [:neg [:expt :x 2]] (reads "−x²")) "a negation takes the power")
  (is (= [:expt [:neg :x] 2] (reads "(−x)²")))
  (is (= [:* [:neg :a] :b] (reads "−a·b")) "a negation binds tighter than a product")
  (is (= [:neg [:* :a :b]] (reads "−(a·b)")))
  (is (= [:+ [:neg :a] :b] (reads "−a + b")))
  (is (= [:- :x [:neg :y]] (reads "x − −y"))))

(deftest numerals
  (is (= -3 (reads "−3")))
  (is (= -3 (reads "- 3")) "a space does not make a node")
  (is (= [:neg 3] (reads "−(3)")) "parentheses do")
  (is (= [:* -3 :x] (reads "−3·x")) "a negative coefficient")
  (is (= [:neg [:expt 3 2]] (reads "−3^2")) "but the power comes first")
  (is (= [:neg [:expt 3 2]] (reads "−3²")))
  (is (= [:* :x -3] (reads "x·−3")))
  (is (= [:- :x 3] (reads "x −3")) "an infix minus, whatever the spacing")
  (is (= half (reads "1/2")))
  (is (= half (reads "1 / 2")) "a slash between integers is the ratio, whatever the spacing")
  (is (= (num/neg half) (reads "−1/2")))
  (is (= (num/neg half) (reads "−1 / 2")))
  (is (= 2 (reads "4/2")) "a ratio in lowest terms, as the EDN reader reads it")
  (is (= [:/ 1 2] (reads "1/(2)")) "parentheses make the quotient node")
  (is (= [:/ 1 2] (reads "(1)/2")))
  (is (= [:/ [:neg 1] 2] (reads "−(1)/2")))
  (is (= [:* half :x] (reads "1/2·x")))
  (is (= [:* :x half] (reads "x·(1/2)")))
  (is (= [:/ :x 2] (reads "x/2")))
  (is (= [:/ 2 :x] (reads "2/x")))
  (is (= [:/ 1 [:expt 2 3]] (reads "1/2³")) "a power on the denominator keeps it a quotient")
  (is (= [:/ half 3] (reads "1/2/3")) "and a third slash divides the ratio")
  (is (= [:expt :x half] (reads "x^(1/2)")))
  (is (= [:expt :x -1] (reads "x^-1")))
  (is (= [:expt :x -1] (reads "x⁻¹")))
  (is (= [:expt :x 12] (reads "x¹²")))
  (is (= 7 (reads "007")) "leading zeros are not octal")
  (is (= 12345678901234567890 (reads "12345678901234567890"))))

(deftest functions-and-derivatives
  (is (= [:sin :x] (reads "sin x")))
  (is (= [:sin [:* 2 :x]] (reads "sin(2·x)")))
  (is (= [:expt [:sin :x] 2] (reads "sin²x")))
  (is (= [:expt [:sin [:+ :x 1]] 2] (reads "sin²(x + 1)")))
  (is (= [:expt [:sin :x] 2] (reads "sin^2 x")))
  (is (= [:expt [:sin :x] 2] (reads "sin^2(x)")))
  (is (= [:expt [:sin :x] -1] (reads "sin^-1 x")))
  (is (= [:expt [:sin :x] [:+ :n 1]] (reads "sin^(n + 1) x")))
  (is (= [:sin [:expt :x 2]] (reads "sin x²")) "the bare operand takes a power")
  (is (= [:* [:sin :x] :y] (reads "sin x·y")) "but not a product")
  (is (= [:* [:sin 2] :x] (reads "sin 2·x")))
  (is (= [:sin [:cos :x]] (reads "sin cos x")))
  (is (= [:sin [:neg :x]] (reads "sin −x")))
  (is (= [:+ :a [:sin :x]] (reads "a + sin x")))
  (is (= [:* :x [:sin :x]] (reads "x·sin x")))
  (is (= [:abs :x] (reads "abs x")))
  (is (= [:exp [:* 2 :x]] (reads "exp(2·x)")))
  (is (= [:f :a :b :c] (reads "f(a, b, c)")) "any name applies to arguments")
  (is (= [:max :a :b] (reads "max(a,b)")))
  (is (= [:f] (reads "f()")))
  (is (= [:D [:sin [:* 2 :x]] :x] (reads "d/dx sin(2·x)")))
  (is (= [:D [:* 2 :x] :x] (reads "d/dx (2·x)")))
  (is (= [:D [:expt :x 2] :x] (reads "d/dx x²")))
  (is (= [:+ [:D :x :x] :y] (reads "d/dx x + y")))
  (is (= [:* [:D :x :x] :y] (reads "(d/dx x)·y")))
  (is (= [:D :t :t] (reads "d/dt t")))
  (is (= [:pi] (reads "π")))
  (is (= [:pi] (reads "pi")))
  (is (= :e (reads "e")) "e is a variable")
  (is (= :theta (reads "theta")))
  (is (= :θ (reads "θ"))))

(deftest typed-spellings
  (is (= [:+ [:* 2 :x] :y] (reads "2*x + y")))
  (is (= [:* 2 :x] (reads "2 × x")))
  (is (= [:- :x 1] (reads "x - 1")))
  (is (= [:- :x 1] (reads "x – 1")) "an en dash is a minus")
  (is (= [:expt :x 2] (reads "x^2")))
  (is (= [:expt :x 2] (reads "x ** 2")))
  (is (= [:/ :x 2] (reads "x ÷ 2")))
  (is (= [:<< :a 1] (reads "a<<1")))
  (is (= [:>> :a 1] (reads "a >> 1")))
  (is (= [:+ :x :y] (reads "  x+y  ")))
  (is (= [:+ :x :y] (reads "x\n+ y")) "a newline is a space"))

(deftest patterns
  (is (= '[:+ ?a ?b] (reads "?a + ?b")))
  (is (= '[:/ ?x 2] (reads "?x/2")))
  (is (= '[:* [:+ ?x 1] [:+ ?x 1]] (reads "(?x + 1)·(?x + 1)")))
  (is (= '[:sin ?x] (reads "sin ?x")))
  (is (= '[:expt ?x 2] (reads "?x²"))))

(deftest problems
  (is (= {:value nil} (parse/term "")))
  (is (= {:value nil} (parse/term "  ")))
  (is (str/includes? (problem "2x") "a product is written 2·x or 2*x"))
  (is (str/includes? (problem "2x") "missing an operator before x at character 2"))
  (is (str/includes? (problem "x y") "missing an operator before y"))
  (is (str/includes? (problem "(a)(b)") "missing an operator before ("))
  (is (= "nothing after +" (problem "x +")))
  (is (= "nothing after (" (problem "(")))
  (is (= "missing ) for the ( at character 1" (problem "(x")))
  (is (= "missing ) for the ( at character 1, found , at character 3" (problem "(x, 1)")))
  (is (= "unexpected \"]\" at character 3" (problem "(x] + 1")) "an unknown character is named where it is")
  (is (= "unexpected ) at character 2" (problem "x)")))
  (is (= "unexpected ) at character 1" (problem ")")))
  (is (= "unexpected · at character 1" (problem "·x")))
  (is (= "sin needs an argument: sin x or sin(x)" (problem "sin")))
  (is (= "sin needs an argument: sin x or sin(x)" (problem "sin + x")))
  (is (= "d/dx needs a function to differentiate" (problem "d/dx")))
  (is (= "− needs something to negate" (problem "−")))
  (is (= "1.5 is not exact; write a ratio such as 1/2" (problem "1.5")))
  (is (= "0.5 is not exact; write a ratio such as 1/2" (problem "0.5·x")))
  (is (= "1/0: division by zero" (problem "1/0")))
  (is (= "1/0: division by zero" (problem "1 / 0")))
  (is (= "unexpected \"$\" at character 3" (problem "x $ y")))
  (is (= "missing ) after the arguments of f, found the end at character 7" (problem "f(a, b")))
  (is (str/includes? (problem "x -> y") "unexpected ->")))

(deftest rules
  (is (= {:value '[["comm" [:+ ?a ?b] [:+ ?b ?a]]]} (parse/rules "comm: ?a + ?b -> ?b + ?a")))
  (is (= {:value '[["comm" [:+ ?a ?b] [:+ ?b ?a]]
                   ["assoc" [:+ [:+ ?a ?b] ?c] [:+ ?a [:+ ?b ?c]]]]}
         (parse/rules "comm: ?a + ?b → ?b + ?a\n\n  assoc: (?a + ?b) + ?c => ?a + (?b + ?c)\n"))
      "one per line, any arrow, blank lines skipped")
  (is (= {:value '[["add-0" [:+ ?a 0] ?a]]} (parse/rules "add-0: ?a + 0 -> ?a")) "a name may hold a hyphen")
  (is (= {:value nil} (parse/rules "\n  \n")))
  (is (= "line 1: a rule is written name: pattern -> replacement" (:error (parse/rules "?a + ?b -> ?b + ?a"))))
  (is (= "line 1: a rule is written name: pattern -> replacement; the name is missing" (:error (parse/rules ": ?a -> ?a"))))
  (is (= "line 2: a rule is written name: pattern -> replacement; one arrow between the two"
         (:error (parse/rules "comm: ?a + ?b -> ?b + ?a\nassoc: ?a + ?b"))))
  (is (= "line 1: the pattern before the arrow is missing" (:error (parse/rules "comm: -> ?a"))))
  (is (= "line 1: the replacement after the arrow is missing" (:error (parse/rules "comm: ?a ->"))))
  (is (= "line 2: nothing after +" (:error (parse/rules "comm: ?a + ?b -> ?b + ?a\nbad: ?a + -> ?a")))))

;; ---------------------------------------------------------------------------
;; the round trip: what the printer prints, the parser reads

(def as-read parse/as-read)

(defn- round-trips? [t]
  (let [text (notation/term->str t)
        back (parse/term text)]
    (is (= (as-read t) (:value back)) (str (pr-str t) " printed as " (pr-str text) " read as " (pr-str back)))))

(def corpus
  "Terms of every shape the page prints: the printer's own examples,
  bendix's coefficients and n-ary forms, the best terms of the
  lessons, and the awkward numerals."
  [[:+ [:* 2 :x] :y] [:+ :a [:+ :b :c]] [:+ [:+ :a :b] :c] [:* [:+ :x 1] [:+ :x 1]]
   [:+ [:expt [:sin :x] 2] [:expt [:cos :x] 2]] [:expt [:sin [:+ :x 1]] 2] [:expt :x [:+ :n 1]]
   [:expt :x 2] [:expt [:+ :x 1] 3] [:D [:sin [:* 2 :x]] :x] [:D [:* 2 :x] :x] [:<< :a 1]
   [:/ [:* :a 2] 2] [:/ :a [:* :b :c]] [:neg :x] [:- [:+ :a :b]] [:- :a 1] [:* [:sin :x] :y]
   [:+ :a [:sin :x]] [:pi] -1 [:f :a :b :c]
   [:+ [:<< :a 1] [:<< :b 1]] [:+ :a0 :a1 :a2 :a3 :a4] [:+ :a :b 1] [:* 2 [:cos [:* 2 :x]]]
   [:+ [:* :x [:cos :x]] [:sin :x]] [:D [:* :x [:sin :x]] :x] [:* 2 :x [:cos [:+ [:expt :x 2] 1]]]
   [:* 3 [:expt [:+ :x 1] 2]] [:* [:cos [:sin [:sin :x]]] [:cos [:sin :x]] [:cos :x]]
   [:+ [:* :x [:D [:abs :x] :x]] [:abs :x]]
   [:* half :x] [:* :x half] [:+ half :x] [:* -1 :x] [:+ [:* 2 :x] -3] [:* :x -3] [:- :x -3]
   [:expt :x -1] [:expt :x -2] [:expt -2 2] [:expt half 2] [:expt :x half] [:expt :x 100]
   [:expt [:sin :x] 100] [:expt [:sin :x] -1] [:expt [:sin 3] 2] [:* [:expt [:sin :x] 2] :y]
   [:* [:expt [:sin [:+ :x 1]] 2] :y]
   [:/ 1 2] [:/ -1 2] [:/ 1 -2] [:/ 3 :y] [:/ half 3] [:/ 1 half] [:/ :x half] [:/ half :x]
   [:neg 3] [:neg -3] [:neg half] [:neg [:* :a :b]] [:neg [:sin :x]] [:neg [:/ :a :b]] [:neg [:expt :x 2]]
   [:- 1 half] [:- 1 [:expt [:cos :x] 2]] [:sin -2] [:expt [:sin -2] 2] [:D -2 :x] [:D :x :x]
   [:* [:D :x :x] :y] [:+ [:D :x :x] :y] [:D [:expt :x 2] :x] [:D [:sin :x] :x]
   [:<< [:+ :a :b] 1] [:+ [:<< :a 1] :b] [:>> [:>> :a 1] 1] [:sin [:sin :x]] [:sin [:- :x :y]]
   [:- [:sin :x] 3] [:* 2 [:sin :x]] [:exp :x] [:exp [:* 2 :x]] [:abs :x]
   [:expt :x :y] [:expt [:expt :x :y] :z] [:expt :x [:expt :y :z]] [:expt 2 3]
   '[:/ ?x 2] '[:* [:+ ?x 1] [:+ ?x 1]] '[:sin ?x] '[:+ [:+ ?a ?b] ?c]])

(deftest the-corpus-round-trips
  (doseq [t corpus] (round-trips? t)))

(deftest every-lesson-round-trips
  (doseq [l lessons/all
          values (cons (:values l) (map :values (:alternatives l)))
          [k v] values]
    (case k
      :rules (when (and (vector? v) (seq v) (every? vector? v))   ; data rules, not bendix's maps
               (is (= (mapv (fn [[n lhs rhs]] [n (as-read lhs) (as-read rhs)]) v)
                      (:value (parse/rules (notation/rules->str v))))
                   (str (:title l) " rules")))
      (round-trips? v))))

(deftest every-draw-round-trips
  (doseq [l lessons/all
          seed (range 30)
          :let [values (generate/draw l (generate/stream seed) (:values l))]
          [k v] values]
    (round-trips? v)))
