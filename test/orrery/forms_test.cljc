(ns orrery.forms-test
  (:require [clojure.test :refer [deftest is testing]]
            [orrery.forms :as forms]))

(defn- texts [s] (mapv #(forms/text-of s %) (forms/top-level s)))

(deftest top-level-forms
  (is (= [] (texts "")))
  (is (= [] (texts "  ; only a comment\n")))
  (is (= ["(+ 1 2)" "x" "[3 4]"] (texts "(+ 1 2) x\n[3 4] ; done")))
  (testing "strings, comments and characters hide their brackets"
    (is (= ["(str \")\" \\( \"\\\"\")" ":k"] (texts "(str \")\" \\( \"\\\"\") ; (\n:k")))
    (is (= ["(f \\newline)" "(g)"] (texts "(f \\newline) (g)")))
    (is (= ["#\"[(]\"" "1"] (texts "#\"[(]\" 1"))))
  (testing "a prefix belongs to the form after it"
    (is (= ["'(1 2)" "@state" "#{1}" "#(inc %)" "^:m [x]" "#?(:clj 1)" "`(a ~b ~@c)"]
           (texts "'(1 2) @state #{1} #(inc %) ^:m [x] #?(:clj 1) `(a ~b ~@c)")))
    (is (= ["#inst \"2026\"" "##Inf"] (texts "#inst \"2026\" ##Inf"))))
  (testing "a discarded form is left out"
    (is (= ["(a)" "(c)"] (texts "(a) #_(b) (c)"))))
  (testing "a form left open is last"
    (let [fs (forms/top-level "(a) (b [c")]
      (is (= [nil true] (mapv :open? fs)))
      (is (= {:start 4 :end 9 :open? true} (peek fs)))))
  (testing "a stray closer is a form of its own"
    (is (= ["(a)" ")"] (texts "(a))")))))

(deftest completeness
  (is (forms/complete? ""))
  (is (forms/complete? "(+ 1 2)"))
  (is (forms/complete? "(str \"(\") ; (("))
  (is (not (forms/complete? "(+ 1")))
  (is (not (forms/complete? "(str \"abc")))
  (is (not (forms/complete? "'")))
  (is (not (forms/complete? "^:m"))))

(deftest the-form-at-the-caret
  (let [s "(a 1)\n\n(b\n  2)  (c)"
        at #(some->> (forms/form-at s %) (forms/text-of s))]
    (is (= "(a 1)" (at 0)))
    (is (= "(a 1)" (at 3)))
    (is (= "(a 1)" (at 5)) "just after the last bracket")
    (is (= "(a 1)" (at 6)) "on the blank line after it")
    (is (= "(b\n  2)" (at 11)))
    (is (= "(b\n  2)" (at 15)) "in the spaces after it")
    (is (= "(c)" (at 17))))
  (is (= "(b)" (forms/text-of "  (b)" (forms/form-at "  (b)" 0))) "before the first form")
  (is (nil? (forms/form-at "  ; nothing" 3))))

(deftest indentation
  (let [at-end #(forms/indent % (count %))]
    (is (= 0 (at-end "(a 1)")))
    (is (= 0 (at-end "")))
    (testing "a vector, a map or a set lines up under its first element"
      (is (= 1 (at-end "[1")))
      (is (= 6 (at-end "(let [x 1")))
      (is (= 4 (at-end "(f {:a 1")))
      (is (= 2 (at-end "#{1"))))
    (testing "a block indents two"
      (is (= 2 (at-end "(let [x 1]")))
      (is (= 2 (at-end "(defn f [x]")))
      (is (= 2 (at-end "(when-let [x 1]")))
      (is (= 2 (at-end "(with-out-str")))
      (is (= 4 (at-end "  (fn [x]"))))
    (testing "a call lines up under its first argument"
      (is (= 8 (at-end "(eg/add g")))
      (is (= 4 (at-end "(:k m")))
      (is (= 1 (at-end "(f")))
      (is (= 1 (at-end "(f\n x")) "under the head when no argument shares its line")
      (is (= 1 (at-end "(1 2"))))
    (testing "nested, on a later line"
      (is (= 10 (at-end "(show! (eg/add g\n               [:+ :b :b])\n       (x y")))
      (is (= 2 (at-end "(let [a 1\n      b 2]"))))
    (testing "only the text before the position counts"
      (is (= 2 (forms/indent "(let [x 1]) (more)" 10))))
    (testing "inside a string the line starts at 0"
      (is (= 0 (at-end "(str \"abc"))))
    (testing "a bracket in a string or a character is not a bracket"
      (is (= 0 (at-end "(str \"(\")")))
      (is (= 0 (at-end "(f \\()"))))))
