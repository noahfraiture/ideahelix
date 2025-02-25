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
    (com.intellij.openapi.editor.event
      CaretListener)
    (com.intellij.openapi.editor.impl
      EditorImpl)
    (java.awt.event
      KeyEvent)))


;; We're allowed to use "thread-unsafe" mutable state since events are coming within 1 thread
;; which is blocked until it is decided what to do with an event.
(defonce state (volatile! {}))


(def get-prefix
  (memoize
    (fn [state]
      (if-let [prefix-vec (get state :prefix)]
        (Integer/parseInt (apply str prefix-vec))
        1))))


(defkeymap
  editor-handler

  (:any
    (KeyEvent/VK_ESCAPE
      "Back to normal mode"
      [state document caret]
      (when (= :insert (:mode state)) (leave-insert-mode document caret))
      [state project editor]
      (let [new-state (assoc state :mode :normal :prefix nil)]
        (if (= :insert (:mode state))
          (do (finish-undo project editor (:mark-action state))
              (dissoc new-state :mark-action))
          new-state))))

  ((:or :normal :select)
   (\u "Undo"
       [editor] (actions editor IdeActions/ACTION_UNDO))

   (Character/isDigit "Add prefix arg" :keep-prefix [char state] (update state :prefix (fnil conj []) char))
   (\d "Delete selections" :undoable :write
       [document caret] (delete-selection-contents document caret))
   (\c "Replace selections" :write
       [state project editor document]
       (let [start (start-undo project editor)]
         (for-each-caret editor #(do (delete-selection-contents document %)
                                     (into-insert-mode-prepend %)))
         (assoc state :mode :insert :prefix nil :mark-action start)))
   (\a "Append to selections"
       [caret] (into-insert-mode-append caret)
       [project editor state]
       (assoc state :mode :insert :prefix nil :mark-action (start-undo project editor)))
   (\i "Prepend to selections"
       [caret] (into-insert-mode-prepend caret)
       [project editor state]
       (assoc state :mode :insert :prefix nil :mark-action (start-undo project editor)))
   ((:or (:alt \;) (:alt \u2026)) "Flip selection" :undoable
                                  [caret] (flip-selection caret))
   ((:or (:alt \:) (:alt \u00DA)) "Make selections forward" :undoable
                                  [caret] (ensure-selection-forward caret))
   (\; "Shrink selections to 1 char" :undoable
       [document caret] (shrink-selection document caret))
   (\, "Drop all selections but primary" :undoable
       [editor] (keep-primary-selection editor))
   (\x "Select whole lines extending" :undoable
       [state document caret]
       (dotimes [_ (get-prefix state)] (select-lines document caret :extend true)))
   (\X "Select whole lines" :undoable
       [document caret] (select-lines document caret :extend false))
   (\C "Add selections below" :undoable
       [state editor caret]
       (add-selection-below editor caret)))

  (:normal
    (\g "Goto mode" :keep-prefix [state] (assoc state :mode :goto))
    (\v "Selection mode" [state] (assoc state :mode :select))
    (\w "Select word forward" :undoable
        [state editor caret]
        (dotimes [_ (get-prefix state)] (move-caret-word-forward editor caret)))
    (\b "Select word backward" :undoable
        [state editor caret]
        (dotimes [_ (get-prefix state)] (move-caret-word-backward editor caret)))
    ((:or \j KeyEvent/VK_DOWN)
     "Move carets down"
     :undoable
     [state document caret]
     (dotimes [_ (get-prefix state)] (move-caret-down document caret))
     [editor] (scroll-to-primary-caret editor))
    ((:or \k KeyEvent/VK_UP)
     "Move carets up"
     :undoable
     [state document caret]
     (dotimes [_ (get-prefix state)] (move-caret-up document caret))
     [editor] (scroll-to-primary-caret editor))
    ((:or \h KeyEvent/VK_LEFT)
     "Move carets left"
     :undoable
     [state document caret]
     (dotimes [_ (get-prefix state)] (move-caret-backward document caret))
     [editor] (scroll-to-primary-caret editor))
    ((:or \l KeyEvent/VK_RIGHT)
     "Move carets right"
     :undoable
     [state document caret]
     (dotimes [_ (get-prefix state)] (move-caret-forward document caret))
     [editor] (scroll-to-primary-caret editor))
    ((:shift \G) "Move to line number" :undoable
                 [state editor document] (move-caret-line-n editor document (get-prefix state))
                 [state] (assoc state :mode :normal)))

  (:select
    (\g "Goto mode extending" :undoable :keep-prefix
        [state] (assoc state :mode :select-goto))
    (\v "Back to normal mode" [state] (assoc state :mode :normal))
    (\w "Select word forward extending" :undoable
        [state document editor caret]
        (dotimes [_ (get-prefix state)] (extending document caret (partial move-caret-word-forward editor))))
    (\b "Select word backward extending" :undoable
        [state document editor caret]
        (dotimes [_ (get-prefix state)] (extending document caret (partial move-caret-word-backward editor))))
    ((:or \j KeyEvent/VK_DOWN)
     "Move carets down extending"
     :undoable
     [state document caret]
     (dotimes [_ (get-prefix state)] (extending document caret (partial move-caret-down document)))
     [editor] (scroll-to-primary-caret editor))
    ((:or \k KeyEvent/VK_UP)
     "Move carets up extending"
     :undoable
     [state document caret]
     (dotimes [_ (get-prefix state)] (extending document caret (partial move-caret-up document)))
     [editor] (scroll-to-primary-caret editor))
    ((:or \h KeyEvent/VK_LEFT)
     "Move carets left extending"
     :undoable
     [state document caret]
     (dotimes [_ (get-prefix state)] (extending document caret (partial move-caret-backward document)))
     [editor] (scroll-to-primary-caret editor))
    ((:or \l KeyEvent/VK_RIGHT)
     "Move carets right extending"
     :undoable
     [state document caret]
     (dotimes [_ (get-prefix state)] (extending document caret (partial move-caret-forward document)))
     [editor] (scroll-to-primary-caret editor))
    ((:shift \G) "Move to line number" :undoable
                 [state editor document]
                 (let [caret (.. editor getCaretModel getPrimaryCaret)]
                   (extending document caret (fn [_] (move-caret-line-n editor document (get-prefix state))))
                   (assoc state :mode :select))))

  (:goto
    (Character/isDigit "Add prefix arg" :keep-prefix [char state] (update state :prefix conj char))
    (\h "Move carets to line start" :undoable
        [document caret] (move-caret-line-start document caret)
        [state] (assoc state :mode :normal))
    (\l "Move carets to line end" :undoable
        [document caret] (move-caret-line-end document caret)
        [state] (assoc state :mode :normal))
    (\g "Move to line number" :undoable
        [state editor document] (move-caret-line-n editor document (get-prefix state))
        [state] (assoc state :mode :normal))
    (_ [state] (assoc state :mode :normal)))

  (:select-goto
    (Character/isDigit "Add prefix arg" :keep-prefix [char state] (update state :prefix conj char))
    (\h "Move carets to line start extending" :undoable
        [document caret] (extending document caret (partial move-caret-line-start document))
        [state] (assoc state :mode :select))
    (\l "Move carets to line end extending" :undoable
        [document caret] (extending document caret (partial move-caret-line-end document))
        [state] (assoc state :mode :select))
    (\g "Move to line number" :undoable
        [state editor document]
        (let [caret (.. editor getCaretModel getPrimaryCaret)]
          (extending document caret (fn [_] (move-caret-line-n editor document (get-prefix state))))
          (assoc state :mode :select)))
    (_ [state] (assoc state :mode :select)))

  (:insert
    ((:or (:ctrl \a) (:ctrl \u0001)) [document caret] (move-caret-line-start document caret))
    ((:or (:ctrl \e) (:ctrl \u0005)) [document caret] (move-caret-line-end document caret))
    (KeyEvent/VK_BACK_SPACE :write [document caret] (backspace document caret))
    (KeyEvent/VK_ENTER :write [document caret] (insert-newline document caret))
    (_ :write [document caret char] (insert-char document caret char))))


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
      (= :pass result) false

      (map? result) (do
                      (.consume event)
                      (vswap! state assoc-in [project editor] result)
                      (ui/update-mode-panel! project result)
                      true)
      :default (do
                 (.consume event)
                 true))))
