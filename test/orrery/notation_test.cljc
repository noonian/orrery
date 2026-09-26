(ns orrery.notation-test
  (:require [bendix.num :as num]
            [clojure.test :refer [deftest is]]
            [orrery.notation :as notation]))

(deftest terms
  (is (= "2·x + y" (notation/term->str [:+ [:* 2 :x] :y])))
  (is (= "a + (b + c)" (notation/term->str [:+ :a [:+ :b :c]])) "right nesting shows")
  (is (= "a + b + c" (notation/term->str [:+ [:+ :a :b] :c])) "left nesting is the chain")
  (is (= "a0 + a1 + a2 + a3 + a4" (notation/term->str [:+ [:+ [:+ [:+ :a0 :a1] :a2] :a3] :a4])))
  (is (= "(x + 1)·(x + 1)" (notation/term->str [:* [:+ :x 1] [:+ :x 1]])))
  (is (= "sin²x + cos²x" (notation/term->str [:+ [:expt [:sin :x] 2] [:expt [:cos :x] 2]])))
  (is (= "sin²(x + 1)" (notation/term->str [:expt [:sin [:+ :x 1]] 2])))
  (is (= "x^(n + 1)" (notation/term->str [:expt :x [:+ :n 1]])))
  (is (= "x²" (notation/term->str [:expt :x 2])))
  (is (= "(x + 1)³" (notation/term->str [:expt [:+ :x 1] 3])))
  (is (= "d/dx sin(2·x)" (notation/term->str [:D [:sin [:* 2 :x]] :x])))
  (is (= "d/dx (2·x)" (notation/term->str [:D [:* 2 :x] :x])))
  (is (= "a << 1" (notation/term->str [:<< :a 1])))
  (is (= "(a·2)/2" (notation/term->str [:/ [:* :a 2] 2])))
  (is (= "a/(b·c)" (notation/term->str [:/ :a [:* :b :c]])))
  (is (= "−x" (notation/term->str [:neg :x])))
  (is (= "−(a + b)" (notation/term->str [:- [:+ :a :b]])))
  (is (= "a − 1" (notation/term->str [:- :a 1])))
  (is (= "(sin x)·y" (notation/term->str [:* [:sin :x] :y])))
  (is (= "a + sin x" (notation/term->str [:+ :a [:sin :x]])))
  (is (= "π" (notation/term->str [:pi])))
  (is (= "−1" (notation/term->str -1)))
  (is (= "f(a, b, c)" (notation/term->str [:f :a :b :c])) "unknown operators print as functions"))

(deftest numerals-print-apart-from-the-nodes-over-them
  (is (= "1/2" (notation/term->str (num/ratio 1 2))) "the number")
  (is (= "1/(2)" (notation/term->str [:/ 1 2])) "the quotient node")
  (is (= "−1/(2)" (notation/term->str [:/ -1 2])))
  (is (= "x/2" (notation/term->str [:/ :x 2])))
  (is (= "1/2·x" (notation/term->str [:* (num/ratio 1 2) :x])))
  (is (= "x·(1/2)" (notation/term->str [:* :x (num/ratio 1 2)])))
  (is (= "(1/2)/3" (notation/term->str [:/ (num/ratio 1 2) 3])))
  (is (= "−3" (notation/term->str -3)) "the number")
  (is (= "−(3)" (notation/term->str [:neg 3])) "the negation node")
  (is (= "−(1/2)" (notation/term->str [:- (num/ratio 1 2)])))
  (is (= "−3·x" (notation/term->str [:* -3 :x])))
  (is (= "−(a·b)" (notation/term->str [:neg [:* :a :b]])) "a negation parenthesizes a product")
  (is (= "−(sin x)" (notation/term->str [:neg [:sin :x]])))
  (is (= "−x²" (notation/term->str [:neg [:expt :x 2]])) "but not a power")
  (is (= "(−2)²" (notation/term->str [:expt -2 2])) "a negative base is parenthesized")
  (is (= "sin(−2)" (notation/term->str [:sin -2])))
  (is (= "x⁻¹" (notation/term->str [:expt :x -1])))
  (is (= "2·x + −3" (notation/term->str [:+ [:* 2 :x] -3]))))

(deftest rules-print-one-per-line
  (is (= "comm: ?a + ?b → ?b + ?a\nassoc: ?a + ?b + ?c → ?a + (?b + ?c)"
         (notation/rules->str '[["comm" [:+ ?a ?b] [:+ ?b ?a]] ["assoc" [:+ [:+ ?a ?b] ?c] [:+ ?a [:+ ?b ?c]]]]))))

(deftest enodes
  (is (= "#3 + #5" (notation/enode->str [:+ 3 5])))
  (is (= "x" (notation/enode->str :x)))
  (is (= "2" (notation/enode->str 2)))
  (is (= "#2^#2" (notation/enode->str [:expt 2 2])) "an e-node's exponent is a class, never a number")
  (is (= "sin #4" (notation/enode->str [:sin 4])))
  (is (= "#7" (notation/class-ref 7))))
