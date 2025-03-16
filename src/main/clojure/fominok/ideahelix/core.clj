;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.core
  (:require
    [cider.nrepl :refer (cider-nrepl-handler)]
    [fominok.ideahelix.editor :refer [handle-editor-event state-atom quit-insert-mode]]
    [fominok.ideahelix.editor.selection :refer :all]
    [fominok.ideahelix.editor.ui :as ui]
    [nrepl.server :refer [start-server]])
  (:import
    (com.intellij.openapi.editor
      Editor)
    (com.intellij.openapi.editor.event
      CaretListener)
    (com.intellij.openapi.editor.impl
      EditorComponentImpl)))


(set! *warn-on-reflection* true)


(defn push-event
  [project focus-owner event]
  (condp instance? focus-owner
    EditorComponentImpl
    (handle-editor-event project (.getEditor ^EditorComponentImpl focus-owner) event)
    false))


(defn- caret-listener
  [editor]
  (reify CaretListener
    (caretPositionChanged
      [_ event]
      (ui/highlight-primary-caret editor event))))


(defn focus-editor
  [project ^Editor editor]
  (let [project-state (or (get @state-atom project) {:mode :normal})
        document (.getDocument editor)]
    (when-not (get-in project-state [:caret-listeners editor])
      (let [listener (caret-listener editor)
            _ (.. editor getCaretModel (addCaretListener listener))]
        (swap! state-atom assoc-in [project :caret-listeners editor] listener)))
    (ui/update-mode-panel! project project-state)
    (when (= (:mode project-state) :normal)
      (.. editor getCaretModel
          (runForEachCaret (fn [caret]
                             (-> (ihx-selection document caret)
                                 (ihx-apply-selection! document))))))))


(defn focus-lost
  [project ^Editor editor]
  (let [state (or (get @state-atom project) {:mode :normal})]
    (quit-insert-mode project state (.getDocument editor))))


(defonce -server (start-server :port 7888 :handler cider-nrepl-handler))
