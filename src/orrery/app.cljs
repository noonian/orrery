(ns orrery.app
  "Mount. The page is one hiccup tree computed from the state and
  rendered by Replicant, pure ClojureScript, no React: state in,
  hiccup out, on every change; every handler in it is data, routed
  through orrery.dispatch."
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
  (let [h (subs (or js/location.hash "") 1)
        n (js/parseInt h 10)]
    (or (some (fn [l] (when (and (= n (:n l)) (lessons/live? l)) (:key l))) lessons/all)
        :blowup)))

(defn ^:dev/after-load reload! [] (render!))

(defn init! []
  (r/set-dispatch! dispatch/dispatch)
  (add-watch state/app-state :render (fn [_ _ _ _] (render!)))
  ;; a read-only hook for the Playwright specs in test/e2e. Not
  ;; window.orrery: that global is the object every orrery.* namespace
  ;; lives on, and assigning it erases them all.
  (set! (.-orreryPage js/window)
        #js {:snapshot (fn [] (clj->js (derived/snapshot @state/app-state)))})
  (js/window.addEventListener "hashchange" (fn [_] (state/load-lesson! (lesson-from-hash))))
  (state/load-lesson! (lesson-from-hash))
  (render!)
  (js/setTimeout selftest/check! 50))
