;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor
  (:require
    [fominok.ideahelix.editor.action :refer [actions]]
    [fominok.ideahelix.editor.modification :refer :all]
    [fominok.ideahelix.editor.movement :refer :all]
    [fominok.ideahelix.editor.selection :refer :all]
    [fominok.ideahelix.editor.ui :as ui]
    [fominok.ideahelix.editor.util :refer [for-each-caret]]
    [fominok.ideahelix.keymap :refer [defkeymap]])
  (:import
    (com.intellij.openapi.actionSystem
      IdeActions)
    (com.intellij.openapi.command
      WriteCommandAction)
    (com.intellij.openapi.editor.event
      CaretListener)
    (com.intellij.openapi.editor.impl
      EditorImpl)
    (java.awt.event
      KeyEvent)))


;; We're allowed to use "thread-unsafe" mutable state since events are coming within 1 thread
;; which is blocked until it is decided what to do with an event.
(defonce state (volatile! {}))


(defn- set-mode
  [state mode]
  (assoc state :mode mode :prefix nil))


(defkeymap
  editor-handler

  (:any
    (KeyEvent/VK_ESCAPE "Back to normal mode"
                        [state document caret] (when (= :insert (:mode state)) (leave-insert-mode document caret))
                        [state project editor] (let [new-state (set-mode state :normal)]
                                                 (if (= :insert (:mode state))
                                                   (do (finish-undo project editor (:mark-action state))
                                                       (dissoc new-state :mark-action))
                                                   new-state)))
    (KeyEvent/VK_SHIFT [] :pass))

  ((:or :normal :select)
   (\u "Undo" [editor] (actions editor IdeActions/ACTION_UNDO))
   (Character/isDigit "Add prefix arg" [char state] (update state :prefix (fnil conj []) char))
   (\d "Delete selections" :undoable
       [write document caret] (delete-selection-contents document caret))
   (\c "Replace selections"
       [state project editor document]
       (let [start (start-undo project editor)]
         (WriteCommandAction/runWriteCommandAction
           project
           (fn []
             (for-each-caret editor #(do (delete-selection-contents document %)
                                         (into-insert-mode-prepend %)))))
         (-> state
             (set-mode :insert)
             (assoc :mark-action start))))

   (\a "Append to selections"
       [caret] (into-insert-mode-append caret)
       [project editor state] (-> state
                                  (set-mode :insert)
                                  (assoc :mark-action (start-undo project editor))))
   (\i "Prepend to selections"
       [caret] (into-insert-mode-prepend caret)
       [project editor state] (-> state
                                  (set-mode :insert)
                                  (assoc :mark-action (start-undo project editor))))
   ((:or (:alt \;) (:alt \u2026)) "Flip selection" :undoable
                                  [caret] (flip-selection caret))
   ((:or (:alt \:) (:alt \u00DA)) "Make selections forward" :undoable
                                  [caret] (ensure-selection-forward caret))
   (\; "Shrink selections to 1 char" :undoable
       [document caret] (shrink-selection document caret))
   (\, "Drop all selections but primary" :undoable
       [editor] (keep-primary-selection editor))
   (\x "Select whole lines extending" :undoable
       [document caret] (select-lines document caret :extend true))
   (\X "Select whole lines" :undoable
       [document caret] (select-lines document caret :extend false))
   (\C "Add selections below" :undoable
       [editor caret] (add-selection-below editor caret)))

  (:normal
    (\g "Goto mode" [state] (set-mode state :goto))
    (\v "Selection mode" [state] (set-mode state :select))
    (\w "Select word forward" :undoable
        [editor caret] (move-caret-word-forward editor caret))
    (\b "Select word backward" :undoable
        [editor caret] (move-caret-word-backward editor caret))
    ((:or \j KeyEvent/VK_DOWN)
     "Move carets down"
     :undoable
     [document caret] (move-caret-down document caret)
     [editor] (scroll-to-primary-caret editor))
    ((:or \k KeyEvent/VK_UP)
     "Move carets up"
     :undoable
     [document caret] (move-caret-up document caret)
     [editor] (scroll-to-primary-caret editor))
    ((:or \h KeyEvent/VK_LEFT)
     "Move carets left"
     :undoable
     [document caret] (move-caret-backward document caret)
     [editor] (scroll-to-primary-caret editor))
    ((:or \l KeyEvent/VK_RIGHT)
     "Move carets right"
     :undoable
     [document caret] (move-caret-forward document caret)
     [editor] (scroll-to-primary-caret editor)))

  (:select
    (\g "Goto mode extending" :undoable
        [state] (set-mode state :select-goto))
    (\v "Back to normal mode" [state] (set-mode state :normal))
    (\w "Select word forward extending" :undoable
        [document editor caret] (extending document caret (partial move-caret-word-forward editor)))
    (\b "Select word backward extending" :undoable
        [document editor caret] (extending document caret (partial move-caret-word-backward editor)))
    ((:or \j KeyEvent/VK_DOWN)
     "Move carets down extending"
     :undoable
     [document caret] (extending document caret (partial move-caret-down document))
     [editor] (scroll-to-primary-caret editor))
    ((:or \k KeyEvent/VK_UP)
     "Move carets up extending"
     :undoable
     [document caret] (extending document caret (partial move-caret-up document))
     [editor] (scroll-to-primary-caret editor))
    ((:or \h KeyEvent/VK_LEFT)
     "Move carets left extending"
     :undoable
     [document caret] (extending document caret (partial move-caret-backward document))
     [editor] (scroll-to-primary-caret editor))
    ((:or \l KeyEvent/VK_RIGHT)
     "Move carets right extending"
     :undoable
     [document caret] (extending document caret (partial move-caret-forward document))
     [editor] (scroll-to-primary-caret editor)))

  (:goto
    (Character/isDigit "Add prefix arg" [char state] (update state :prefix conj char))
    (\h "Move carets to line start" :undoable
        [document caret] (move-caret-line-start document caret)
        [state] (set-mode state :normal))
    (\l "Move carets to line end" :undoable
        [document caret] (move-caret-line-end document caret)
        [state] (set-mode state :normal))
    (\g "Move to line number" :undoable
        [document caret] (move-caret-line-n document caret)
        [state] (set-mode state :normal))
    (_ [state] (set-mode state :normal)))

  (:select-goto
    (Character/isDigit "Add prefix arg" [char state] (update state :prefix conj char))
    (\h "Move carets to line start extending" :undoable
        [document caret] (extending document caret (partial move-caret-line-start document))
        [state] (set-mode state :select))
    (\l "Move carets to line end extending" :undoable
        [document caret] (extending document caret (partial move-caret-line-end document))
        [state] (set-mode state :select))
    (\g "Move to line number extending" :undoable
        [document caret] (extending document caret (partial move-caret-line-n document))
        [state] (set-mode state :select))
    (_ [state] (set-mode state :select)))

  (:insert
    ((:or (:ctrl \a) (:ctrl \u0001)) [document caret] (move-caret-line-start document caret))
    ((:or (:ctrl \e) (:ctrl \u0005)) [document caret] (move-caret-line-end document caret))
    (KeyEvent/VK_BACK_SPACE [write document caret] (backspace document caret))
    (KeyEvent/VK_ENTER [write document caret] (insert-newline document caret))
    (_ [write document caret char] (insert-char document caret char))))


(defn- caret-listener
  [editor]
  (reify CaretListener
    (caretPositionChanged
      [_ event]
      (ui/highlight-primary-caret editor event))))


(defn handle-editor-event
  [project ^EditorImpl editor ^KeyEvent event]
  (let [editor-state (or (get-in @state [project editor]) {:mode :normal})
        result (editor-handler project editor-state editor event)]
    (when-not (:caret-listener editor-state)
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
                      (vswap! state assoc-in [project editor] result)
                      (ui/update-mode-panel! project result)
                      true))))
