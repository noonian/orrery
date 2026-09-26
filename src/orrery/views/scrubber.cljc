(ns orrery.views.scrubber
  "The transport: first, back, play, forward, last, a range over the
  timeline, and the label of the entry on show, in one row; a stop
  button and a note while the engine is still running. summary, when
  given, is a short text beside the label, the counts of the step on
  show.")

(defn scrubber
  [{:keys [step n labels playing? status summary]}]
  (let [running? (= :running status)]
    [:div.scrubber
     [:button {:id "step-first" :on {:click [:step 0]} :disabled (zero? step)} "⏮"]
     [:button {:id "step-prev" :on {:click [:step (dec step)]} :disabled (zero? step)} "◀"]
     (if playing?
       [:button {:id "step-pause" :on {:click [:pause]}} "⏸"]
       [:button {:id "step-play" :on {:click [:play]} :disabled (>= step n)} "▶"])
     [:button {:id "step-next" :on {:click [:step (inc step)]} :disabled (>= step n)} "▶|"]
     [:button {:id "step-last" :on {:click [:step n]} :disabled (>= step n)} "⏭"]
     [:input {:id "scrubber-range" :type "range" :min 0 :max n :value step
              :on {:input [:step-from-range]}}]
     (when running?
       [:button {:id "run-stop" :on {:click [:stop]}} "stop"])
     [:span.scrubber-label (str (nth labels step "") " · " step " of " n)]
     (when summary [:span.scrubber-summary summary])
     (when running? [:span.running "computing the next iteration…"])]))
