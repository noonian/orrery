(ns orrery.input-test
  (:require [clojure.test :refer [deftest is]]
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
