(ns fominok.ideahelix.editor
  (:require [clojure.string :as str]
            [clojure.core.async :refer [<!! >!!] :as a])
  (:import (com.intellij.openapi.wm WindowManager)
           (fominok.ideahelix ModePanel)
           (java.awt.event KeyEvent)))

(defonce state (agent {} :error-handler (fn [_ ex] (println ex))))

(let [s @state]
  s)

(defn update-mode-panel! [project mode]
  (let [id (ModePanel/ID)
        status-bar (.. WindowManager getInstance (getStatusBar project))
        widget (.getWidget status-bar id)]
    (.setText widget (str/upper-case (name mode)))
    (.updateWidget status-bar id)))

(defn set-mode! [project mode]
  (send state
        (fn [s]
          (update-mode-panel! project mode)
          (assoc-in s [project :mode] mode)))
  true)

(defn handle-normal-mode [project ^KeyEvent event]
  (let [event-set
        (cond-> #{(.getKeyChar event)}
                (.isControlDown event) (conj :ctrl)
                (.isAltDown event) (conj :alt))]
    (case event-set
      #{\i} (set-mode! project :insert)
      false)))

(defn handle-editor-event [project ^KeyEvent event]
  (let [result (a/chan 10)]
    ;; Agent is used to access shared state (mode) with possible mutation, processing
    ;; the event in isolation and no more than 1 at a time, think of it as a kind of
    ;; critical section
    (send
      state
      (fn [{{:keys [mode]} project :as s}]
        (>!! result
             (if (= 27 (.getKeyCode event))
               (set-mode! project :normal)
               (case mode
                     :normal (handle-normal-mode project event)
                     false)))
        s))

    (let [r (<!! result)]
      (when r (.consume event))
      r)))
