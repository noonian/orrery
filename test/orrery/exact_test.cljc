(ns orrery.exact-test
  "Exact numbers through the page's seams: read, printed, and refused
  when inexact."
  (:require [clojure.test :refer [deftest is]]
            [bendix.num :as num]
            [orrery.costs :as costs]
            [orrery.input :as input]
            [orrery.lay :as lay]))

(deftest ratios-print-as-written
  (is (= "1/2·x" (lay/term->str [:* (num/div 1 2) :x])))
  (is (= "x^(1/2)" (lay/term->str [:expt :x (num/div 1 2)])))
  (is (= "−1/2·x" (lay/term->str [:* (num/div -1 2) :x])))
  (is (= "d/dx (x·(sin x))" (lay/term->str [:D [:* :x [:sin :x]] :x]))))

(deftest ratios-read-exact
  (is (= {:term [:* (num/div 1 2) :x]} (input/read-term "[:* 1/2 :x]")))
  (is (= {:pattern [:expt '?x (num/div -1 2)]} (input/read-pattern "[:expt ?x -1/2]")))
  (is (re-find #"not exact" (:error (input/read-term "[:* 0.5 :x]"))))
  (is (re-find #"not exact" (:error (input/read-pattern "[:expt ?x 0.5]")))))

(deftest costs-display
  (is (= "3" (costs/cost-str 3)))
  (is (= "385/64" (costs/cost-str (num/div 385 64))))
  (is (= "385/64" (costs/cost-str 6.015625)) "a double that is a multiple of 1/64, as in JavaScript")
  (is (= "[0 385/64]" (costs/cost-str [0 (num/div 385 64)]))))
