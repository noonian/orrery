(ns orrery.views.scrubber
  "The transport: first, back, play, forward, last, and a range over
  the timeline. Labels name each entry.")

(defn scrubber
  [{:keys [step n labels playing? status on-step on-play on-pause on-stop]}]
  (let [running? (= :running status)]
    [:div
     [:div.scrubber
      [:button {:id "step-first" :on {:click #(on-step 0)} :disabled (zero? step)} "⏮"]
      [:button {:id "step-prev" :on {:click #(on-step (dec step))} :disabled (zero? step)} "◀"]
      (if playing?
        [:button {:id "step-pause" :on {:click #(on-pause)}} "⏸"]
        [:button {:id "step-play" :on {:click #(on-play)} :disabled (>= step n)} "▶"])
      [:button {:id "step-next" :on {:click #(on-step (inc step))} :disabled (>= step n)} "▶|"]
      [:button {:id "step-last" :on {:click #(on-step n)} :disabled (>= step n)} "⏭"]
      [:input {:id "scrubber-range" :type "range" :min 0 :max n :value step
               :on {:input (fn [e] (on-step (js/parseInt (.. e -target -value) 10)))}}]
      (when running?
        [:button {:id "run-stop" :on {:click #(on-stop)}} "stop"])]
     [:div.status
      [:span.scrubber-label (str (nth labels step "") " · " step " of " n)]
      (when running? [:span.running "  computing the next iteration…"])]]))
