;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor
  (:require [fominok.ideahelix.editor.ui :as ui]
            [fominok.ideahelix.keymap :refer [defkeymap]]
            [fominok.ideahelix.editor.movement :refer :all]
            [fominok.ideahelix.editor.selection :refer :all]
            [fominok.ideahelix.editor.action :refer [actions]]
            [fominok.ideahelix.editor.modification :refer :all])
  (:import
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


(defn- set-mode [state mode]
  (assoc state :mode mode :prefix nil))

(defkeymap
  editor-handler

  (:any
    (KeyEvent/VK_ESCAPE
      [state document caret] (when (= :insert (:mode state)) (leave-insert-mode document caret))
      [state] (set-mode state :normal))
    (KeyEvent/VK_SHIFT [] :pass))

  ((:or :normal :select)
   (Character/isDigit [char state] (update state :prefix (fnil conj []) char))
   (\d [write document caret] (delete-selection-contents document caret))
   (\a
     [caret] (into-insert-mode-append caret)
     [state] (set-mode state :insert))
   (\i
     [caret] (into-insert-mode-prepend caret)
     [state] (set-mode state :insert))
   ((:or (:alt \;) (:alt \u2026)) [caret] (flip-selection caret))
   ((:or (:alt \:) (:alt \u00DA)) [caret] (ensure-selection-forward caret))
   (\; [document caret] (shrink-selection document caret))
   (\, [editor] (keep-primary-selection editor))
   (\x [document caret] (select-lines document caret :extend true))
   (\X [document caret] (select-lines document caret :extend false))
   (\C [editor caret] (add-selection-below editor caret)))

  (:normal
    (\g [state] (set-mode state :goto))
    (\v [state] (set-mode state :select))
    (\w [editor caret] (move-caret-word-forward editor caret))
    (\b [editor caret] (move-caret-word-backward editor caret))
    ((:or \j KeyEvent/VK_DOWN)
     [document caret] (move-caret-down document caret)
     [editor] (scroll-to-primary-caret editor))
    ((:or \k KeyEvent/VK_UP)
     [document caret] (move-caret-up document caret)
     [editor] (scroll-to-primary-caret editor))
    ((:or \h KeyEvent/VK_LEFT)
     [document caret] (move-caret-backward document caret)
     [editor] (scroll-to-primary-caret editor))
    ((:or \l KeyEvent/VK_RIGHT)
     [document caret] (move-caret-forward document caret)
     [editor] (scroll-to-primary-caret editor)))

  (:select
    (\g [state] (set-mode state :select-goto))
    (\v [state] (set-mode state :normal))
    (\w [document editor caret] (extending document caret (partial move-caret-word-forward editor)))
    (\b [document editor caret] (extending document caret (partial move-caret-word-backward editor)))
    ((:or \j KeyEvent/VK_DOWN)
     [document caret] (extending document caret (partial move-caret-down document))
     [editor] (scroll-to-primary-caret editor))
    ((:or \k KeyEvent/VK_UP)
     [document caret] (extending document caret (partial move-caret-up document))
     [editor] (scroll-to-primary-caret editor))
    ((:or \h KeyEvent/VK_LEFT)
     [document caret] (extending document caret (partial move-caret-backward document))
     [editor] (scroll-to-primary-caret editor))
    ((:or \l KeyEvent/VK_RIGHT)
     [document caret] (extending document caret (partial move-caret-forward document))
     [editor] (scroll-to-primary-caret editor)))

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

  (:select-goto
    (Character/isDigit [char state] (update state :prefix conj char))
    (\h
      [document caret] (extending document caret (partial move-caret-line-start document))
      [state] (set-mode state :select))
    (\l
      [document caret] (extending document caret (partial move-caret-line-end document))
      [state] (set-mode state :select))
    (\g
      [document caret] (extending document caret (partial move-caret-line-n document))
      [state] (set-mode state :select))
    (\s
      [editor] (actions editor "EditorLineStart")
      [state] (set-mode state :normal))
    (_ [state] (set-mode state :select)))

  (:insert
    ((:or (:ctrl \a) (:ctrl \u0001)) [document caret] (move-caret-line-start document caret))
    ((:or (:ctrl \e) (:ctrl \u0005)) [document caret] (move-caret-line-end document caret))
    (KeyEvent/VK_BACK_SPACE [write document caret] (backspace document caret))
    (_ [write document caret char] (insert-char document caret char))))

(defn- caret-listener [editor]
  (reify CaretListener
    (caretPositionChanged [_ event]
      (ui/highlight-primary-caret editor event))))

(defn handle-editor-event [project ^EditorImpl editor ^KeyEvent event]
  (let [proj-state (get @state project)
        result (editor-handler project proj-state editor event)]
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
