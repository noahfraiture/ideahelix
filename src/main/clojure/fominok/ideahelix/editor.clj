;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor
  (:require [fominok.ideahelix.editor.ui :as ui]
            [fominok.ideahelix.keymap :refer [defkeymap]]
            [fominok.ideahelix.editor.movement :refer :all]
            [fominok.ideahelix.editor.selection :refer :all])
  (:import
    (com.intellij.openapi.actionSystem ActionManager ActionPlaces AnActionEvent)
    (com.intellij.openapi.editor.event CaretListener)
    (com.intellij.openapi.editor.impl EditorImpl)
    (java.awt.event KeyEvent)))


;; We're allowed to use "thread-unsafe" mutable state since events are coming within 1 thread
;; which is blocked until it is decided what to do with an event.
(defonce state (volatile! {}))

(defn set-mode! [project mode]
  (vswap! state assoc-in [project :mode] mode)
  (ui/update-mode-panel! project (get @state project))
  :consume)

(defn- actions [^EditorImpl editor & action-names]
  (let [data-context (.getDataContext editor)]
    (doseq [action-name action-names]
      (let [action (.getAction (ActionManager/getInstance) action-name)]
        (.actionPerformed
          action
          (AnActionEvent/createFromDataContext
            ActionPlaces/KEYBOARD_SHORTCUT nil data-context))))))

(defn- set-mode [state mode]
  (assoc state :mode mode :prefix nil))

(defkeymap
  editor-handler
  (:any
    (KeyEvent/VK_ESCAPE [state] (set-mode state :normal))
    (KeyEvent/VK_SHIFT [] :pass))
  (:normal
    (Character/isDigit [char state] (update state :prefix (fnil conj []) char))
    (\i [state] (set-mode state :insert))
    (\g [state] (set-mode state :goto))
    (\v [state] (set-mode state :select))
    (\w [editor] (actions editor "EditorUnSelectWord" "EditorNextWordWithSelection"))
    (\b [editor] (actions editor "EditorUnSelectWord" "EditorPreviousWordWithSelection"))
    (\x [document caret]  (select-lines document caret :extend true))
    (\X [document caret]  (select-lines document caret :extend false))
    ((:or \j KeyEvent/VK_DOWN) [editor] (actions editor "EditorDown"))
    ((:or \k KeyEvent/VK_UP) [editor] (actions editor "EditorUp"))
    ((:or \h KeyEvent/VK_LEFT) [caret] (move-caret-left caret))
    ((:or \l KeyEvent/VK_RIGHT) [document caret] (move-caret-right document caret)))
  (:select
    (\v [state] (set-mode state :normal))
    (\w [editor] (actions editor "EditorNextWordWithSelection"))
    (\b [editor] (actions editor "EditorPreviousWordWithSelection"))
    ((:or \h KeyEvent/VK_LEFT) [caret] (extend caret move-caret-left))
    ((:or \l KeyEvent/VK_RIGHT) [document caret] (extend caret (partial move-caret-right document))))
  (:goto
    (Character/isDigit [char state] (update state :prefix conj char))
    (\h
      [document caret] (move-caret-line-start document caret)
      [state] (set-mode state :normal))
    (\l
      [document caret] (move-caret-line-end document caret)
      [state] (set-mode state :normal))
    (\g
      [document caret] (move-caret-line-n document caret)
      [state] (set-mode state :normal))
    (\s
      [editor] (actions editor "EditorLineStart")
      [state] (set-mode state :normal))
    (_ [state] (set-mode state :normal)))
  (:insert
    ((:ctrl \a) [document caret] (move-caret-line-start document caret))
    ((:ctrl \e) [document caret] (move-caret-line-end document caret))
    ((:ctrl \u0001) [document caret] (move-caret-line-start document caret))
    ((:ctrl \u0005) [document caret] (move-caret-line-end document caret))
    (_ [] :pass)))

(defn- caret-listener [editor]
  (reify CaretListener
    (caretPositionChanged [_ event]
      (ui/highlight-primary-caret editor event))))

(defn handle-editor-event [project ^EditorImpl editor ^KeyEvent event]
  (let [proj-state (get @state project)
        result (editor-handler proj-state editor event)]
    (when-not (get-in proj-state [editor :caret-listener])
      (let [listener (caret-listener editor)]
        (.. editor getCaretModel (addCaretListener listener))
        (vswap! state assoc-in [project editor :caret-listener] listener)))
    (cond
      (nil? result) (do
                      (.consume event)
                      true)
      (= :pass result) false
      (map? result) (do
                      (.consume event)
                      (vswap! state assoc project result)
                      (ui/update-mode-panel! project result)
                      true))))
