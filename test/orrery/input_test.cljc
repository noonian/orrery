(ns orrery.input-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [orrery.input :as input]))

(deftest terms
  (is (= {:term [:+ [:* 2 :x] :y]} (input/read-term "[:+ [:* 2 :x] :y]")))
  (is (= {:term :x} (input/read-term ":x")))
  (is (:error (input/read-term "[:+ 1")))
  (is (:error (input/read-term "")))
  (is (:error (input/read-term "[1 2]")))
  (is (:error (input/read-term "[:+ \"s\" 1]")))
  (is (= 5 (input/leaf-count [:+ [:+ [:+ [:+ :a0 :a1] :a2] :a3] :a4]))))

(deftest rules
  (let [{:keys [rules error]} (input/read-rules "[[\"comm\" [:+ ?a ?b] [:+ ?b ?a]]]")]
    (is (nil? error))
    (is (= '[["comm" [:+ ?a ?b] [:+ ?b ?a]]] rules) "rules come back as data"))
  (is (= {:pattern '[:/ ?x 2]} (input/read-pattern "[:/ ?x 2]")))
  (is (:error (input/read-pattern "[:/ x 2]")) "a bare symbol is not a variable")
  (is (:error (input/read-rules "[]")))
  (is (:error (input/read-rules "[[\"x\" [:+ ?a ?b]]]")))
  (is (:error (input/read-rules "[[\"x\" [:+ ?a ?b] [:+ ?a ?c]]]")) "an unbound variable on the right")
  (is (:error (input/read-rules "[[\"x\" [:+ ?a 1] 1] [\"x\" [:+ ?a 0] ?a]]")) "distinct names"))

(deftest notation-reads-too
  (is (= {:term [:+ [:* 2 :x] :y]} (input/read-term "2·x + y")))
  (is (= {:term [:+ [:* 2 :x] :y]} (input/read-term "2*x + y")))
  (is (= {:term :x} (input/read-term "x")) "a bare name is a variable")
  (is (= {:term 2} (input/read-term "2")))
  (is (= {:term -3} (input/read-term "-3")))
  (is (= {:term 1} (input/read-term "1")) "a number is a term")
  (is (input/native? "[:+ :a :b]"))
  (is (input/native? "  :x"))
  (is (not (input/native? "x + 1")))
  (is (not (input/native? "?x/2")))
  (is (str/includes? (:error (input/read-term "2x")) "a product is written 2·x or 2*x"))
  (is (str/includes? (:error (input/read-term "[:+ 1")) "could not read that") "native text keeps the EDN reader's message")
  (is (str/includes? (:error (input/read-term "?x + 1")) "pattern variable") "a term has no ?variables")
  (is (str/includes? (:error (input/read-term "")) "2·x + y"))
  (is (= {:pattern '[:/ ?x 2]} (input/read-pattern "?x/2")))
  (is (= {:pattern '[:* [:+ ?x 1] [:+ ?x 1]]} (input/read-pattern "(?x + 1)·(?x + 1)")))
  (is (= {:pattern '[:/ ?x 2]} (input/read-pattern "[:/ ?x 2]")))
  (is (= '[["comm" [:+ ?a ?b] [:+ ?b ?a]]] (:rules (input/read-rules "comm: ?a + ?b -> ?b + ?a"))))
  (is (= '[["comm" [:+ ?a ?b] [:+ ?b ?a]] ["assoc" [:+ [:+ ?a ?b] ?c] [:+ ?a [:+ ?b ?c]]]]
         (:rules (input/read-rules "comm: ?a + ?b → ?b + ?a\nassoc: (?a + ?b) + ?c → ?a + (?b + ?c)"))))
  (is (str/includes? (:error (input/read-rules "comm: ?a + ?b -> ?b + ?c")) "?c") "an unbound variable on the right, in notation too")
  (is (str/includes? (:error (input/read-rules "comm ?a + ?b -> ?b + ?a")) "line 1: a rule is written name: pattern -> replacement"))
  (is (str/includes? (:error (input/read-rules "x: ?a + 1 -> 1\nx: ?a + 0 -> ?a")) "distinct"))
  (is (str/includes? (:error (input/read-rules "")) "name: pattern -> replacement")))
