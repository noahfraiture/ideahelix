;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.action
  (:import (com.intellij.openapi.actionSystem ActionManager ActionPlaces AnActionEvent)
           (com.intellij.openapi.editor.impl EditorImpl)))

(defn actions [^EditorImpl editor & action-names]
  (let [data-context (.getDataContext editor)]
    (doseq [action-name action-names]
      (let [action (.getAction (ActionManager/getInstance) action-name)]
        (.actionPerformed
          action
          (AnActionEvent/createFromDataContext
            ActionPlaces/KEYBOARD_SHORTCUT nil data-context))))))