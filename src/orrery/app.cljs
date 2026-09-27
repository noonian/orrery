(ns orrery.app
  "Mounts the page. The page is one hiccup tree, computed from the
  state and rendered by Replicant. It is pure ClojureScript, with no
  React. Every change of the state renders the tree again: state in,
  hiccup out. Every handler in the tree is data, and orrery.dispatch
  routes it."
  (:require [orrery.derived :as derived]
            [orrery.dispatch :as dispatch]
            [orrery.lessons :as lessons]
            [orrery.selftest :as selftest]
            [orrery.state :as state]
            [orrery.views.lesson :as lesson]
            [replicant.dom :as r]))

(defn render! []
  (r/render (js/document.getElementById "app") (lesson/page @state/app-state)))

(defn- lesson-from-hash []
  (let [l (lessons/by-address (subs (or js/location.hash "") 1))]
    (if (and l (lessons/live? l)) (:key l) lessons/start)))

(defn- watch!
  "Adds the watch that makes the page follow its state. On every
  change the watch first calls `state/changed!`, which does what the
  change asks of the browser, such as starting the run loop. Then it
  renders the page."
  []
  (add-watch state/app-state :render (fn [_ _ old new] (state/changed! old new) (render!))))

(defn ^:dev/after-load reload! [] (watch!) (render!))

(defn init! []
  (r/set-dispatch! dispatch/dispatch)
  (watch!)
  ;; A read-only hook for the Playwright specs in test/e2e. It is not
  ;; named window.orrery, because that global is the object every
  ;; orrery.* namespace lives on, and assigning it erases them all.
  (set! (.-orreryPage js/window)
        #js {:snapshot (fn [] (clj->js (derived/snapshot @state/app-state)))})
  (js/window.addEventListener "keydown" #(dispatch/key! %))
  (js/window.addEventListener "hashchange" (fn [_] (state/load-lesson! (lesson-from-hash))))
  (state/load-lesson! (lesson-from-hash))
  (render!)
  (js/setTimeout selftest/check! 50))
