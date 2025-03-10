;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.core
  (:require
    [cider.nrepl :refer (cider-nrepl-handler)]
    [fominok.ideahelix.editor :refer [handle-editor-event state]]
    [fominok.ideahelix.editor.selection :refer :all]
    [fominok.ideahelix.editor.ui :as ui]
    [nrepl.server :refer [start-server]])
  (:import
    (com.intellij.ide.actions.searcheverywhere
      SearchEverywhereManager)
    (com.intellij.openapi.editor
      Editor)
    (com.intellij.openapi.editor.event
      CaretListener)
    (com.intellij.openapi.editor.impl
      EditorComponentImpl)
    (com.intellij.openapi.fileEditor
      FileEditorManager
      FileEditorManagerListener)
    (com.intellij.openapi.project
      Project)))


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
  (let [project-state (get @state project)
        editor-state (or (get project-state editor) {:mode :normal})
        document (.getDocument editor)]
    (when-not (:caret-listener editor-state)
      (let [listener (caret-listener editor)
            _ (.. editor getCaretModel (addCaretListener listener))
            updated-state (assoc editor-state :caret-listener listener)]
        (swap! state assoc-in [project editor] updated-state)))
    (ui/update-mode-panel! project editor-state)
    (when (= (:mode editor-state) :normal)
      (.. editor getCaretModel
          (runForEachCaret (fn [caret]
                             (-> (ihx-selection document caret)
                                 (ihx-apply-selection! document))))))))


(defn init
  [^Project project])


(defonce -server (start-server :port 7888 :handler cider-nrepl-handler))
