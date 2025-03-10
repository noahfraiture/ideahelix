;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor
  (:require
    [fominok.ideahelix.editor.action :refer [actions]]
    [fominok.ideahelix.editor.jumplist :refer :all]
    [fominok.ideahelix.editor.modification :refer :all]
    [fominok.ideahelix.editor.registers :refer :all]
    [fominok.ideahelix.editor.selection :refer :all]
    [fominok.ideahelix.editor.ui :as ui]
    [fominok.ideahelix.editor.util :refer [deep-merge]]
    [fominok.ideahelix.keymap :refer [defkeymap]])
  (:import
    (com.intellij.ide.actions.searcheverywhere
      SearchEverywhereManager)
    (com.intellij.openapi.actionSystem
      ActionPlaces
      AnActionEvent
      IdeActions)
    (com.intellij.openapi.editor.impl
      EditorImpl)
    (java.awt.event
      KeyEvent)))


(defonce state (atom {}))


(def get-prefix
  (memoize
    (fn [state]
      (if-let [prefix-vec (get state :prefix)]
        (min 10000 (Integer/parseInt (apply str prefix-vec)))
        1))))


(defn- quit-insert-mode
  [project editor-state editor document]
  (restore-selections editor-state editor document)
  (finish-undo project editor (:mark-action editor-state))
  (-> editor-state
      (dissoc :mark-action)
      (assoc :mode :normal :prefix nil)))


(defn- into-insert-mode
  [project
   editor-state
   editor
   & {:keys [dump-selections insertion-kind]
      :or {dump-selections true insertion-kind :prepend}}]
  (-> editor-state
      (#(if dump-selections
          (dump-drop-selections! % editor (.getDocument editor))
          %))
      (assoc :mode :insert
             :debounce true
             :insertion-kind insertion-kind
             :prefix nil
             :mark-action (start-undo project editor))))


(defkeymap
  editor-handler

  (:any
    (KeyEvent/VK_ESCAPE
      "Back to normal mode"
      [state project editor document]
      (if (= :insert (:mode state))
        (quit-insert-mode project state editor document)
        (assoc state :mode :normal :prefix nil :pre-selections nil :insertion-kind nil))))

  (:find-char
    (_ [document caret char] (find-char document caret char)
       [state] (assoc state :mode (:previous-mode state))))

  ((:or :normal :select)
   (\space
     "Space menu"
     [state] (assoc state :mode :space))
   (\t
     "Find till char"
     [state] (assoc state :mode :find-char
                    :previous-mode (:mode state)))
   (\u
     "Undo"
     [editor] (actions editor IdeActions/ACTION_UNDO)
     [document caret] (-> (ihx-selection document caret)
                          (ihx-apply-selection! document)))
   ((:shift \U)
    "Redo"
    [editor] (actions editor IdeActions/ACTION_REDO)
    [document caret] (-> (ihx-selection document caret)
                         (ihx-apply-selection! document)))
   (\y
     "Yank"
     [project-state editor document]
     (let [registers (copy-to-register (:registers project-state) editor document)]
       (assoc project-state :registers registers)))
   (\o
     "New line below" :write :scroll
     [editor document caret]
     (do (-> (ihx-selection document caret)
             (ihx-move-line-end editor document)
             (ihx-apply-selection! document))
         (.setSelection caret (.getSelectionEnd caret) (.getSelectionEnd caret)))
     [project editor state]
     (let [new-state (into-insert-mode project state editor :dump-selections false)]
       (actions editor IdeActions/ACTION_EDITOR_ENTER)
       new-state))
   ((:shift \O)
    "New line above" :write :scroll
    [editor document caret]
    (do (-> (ihx-selection document caret)
            (ihx-move-relative! :lines -1)
            (ihx-move-line-end editor document)
            (ihx-apply-selection! document))
        (.setSelection caret (.getSelectionEnd caret) (.getSelectionEnd caret)))
    [project editor state]
    (let [new-state (into-insert-mode project state editor :dump-selections false)]
      (actions editor IdeActions/ACTION_EDITOR_ENTER)
      new-state))
   ((:shift \%)
    "Select whole buffer"
    [editor document] (select-buffer editor document))
   (\s
     "Select in selections"
     [project editor document] (select-in-selections project editor document))
   (Character/isDigit
     "Add prefix arg" :keep-prefix
     [char state] (update state :prefix (fnil conj []) char))
   (\d
     "Delete selections" :undoable :write
     [project-state editor document]
     (delete-selections project-state editor document))
   (\c
     "Replace selections" :write
     [project-state project editor document]
     (replace-selections project-state project editor document))
   (\a
     "Append to selections"
     [document caret]
     (-> (ihx-selection document caret)
         ihx-make-forward
         (ihx-apply-selection! document))
     [project editor state document]
     (let [new-state (into-insert-mode project state editor :insertion-kind :append)]
       (actions editor IdeActions/ACTION_EDITOR_MOVE_CARET_RIGHT)
       new-state))
   ((:shift \A)
    "Append to line"
    [editor document caret]
    (-> (ihx-selection document caret)
        (ihx-move-line-end editor document)
        ihx-shrink-selection
        (ihx-apply-selection! document))
    [project editor state]
    (into-insert-mode project state editor))
   (\i
     "Prepend to selections"
     [document caret]
     (-> (ihx-selection document caret)
         ihx-make-backward
         (ihx-apply-selection! document))
     [project editor state]
     (into-insert-mode project state editor))
   ((:shift \I)
    "Prepend to lines"
    [editor document caret]
    (-> (ihx-selection document caret)
        (ihx-move-line-start editor document)
        ihx-shrink-selection
        (ihx-apply-selection! document))
    [project editor state]
    (into-insert-mode project state editor))
   ((:or (:alt \;) (:alt \u2026))
    "Flip selection" :scroll
    [document caret] (-> (ihx-selection document caret)
                         flip-selection
                         (ihx-apply-selection! document)))
   ((:or (:alt \:) (:alt \u00DA))
    "Make selections forward"
    [document caret]
    (-> (ihx-selection document caret)
        ihx-make-forward
        (ihx-apply-selection! document)))
   (\;
     "Shrink selections to 1 char"
     [document caret]
     (-> (ihx-selection document caret)
         ihx-shrink-selection
         (ihx-apply-selection! document)))
   (\,
     "Drop all selections but primary"
     [editor] (keep-primary-selection editor))
   (\x
     "Select whole lines extending" :scroll
     [state editor document caret]
     (dotimes [_ (get-prefix state)]
       (-> (ihx-selection document caret)
           (ihx-select-lines editor document :extend true)
           (ihx-apply-selection! document))))
   ((:shift \X)
    "Select whole lines" :scroll
    [editor document caret]
    (-> (ihx-selection document caret)
        (ihx-select-lines editor document)
        (ihx-apply-selection! document)))
   ((:shift \C)
    "Add selections below"
    [state editor caret]
    (add-selection-below editor caret))
   ((:or (:ctrl \o) (:ctrl \u000f))
    "Jump backward"
    [project-state project editor document]
    (jumplist-backward! project-state project))
   ((:or (:ctrl \i) (:ctrl \u0009))
    "Jump forward"
    [project-state project editor document]
    (jumplist-forward! project-state project))
   ((:or (:ctrl \s) (:ctrl \u0013))
    "Add to jumplist"
    [project-state project document]
    (jumplist-add  project project-state)))

  (:normal
    (\g "Goto mode" :keep-prefix [state] (assoc state :mode :goto))
    (\v "Selection mode" [state] (assoc state :mode :select))
    (\p
      "Paste" :undoable :write
      [project-state editor document]
      (paste-register (:registers project-state) editor document :select true))
    (\w
      "Select word forward" :scroll
      [state editor document caret]
      (dotimes [_ (get-prefix state)]
        (-> (ihx-selection document caret)
            (ihx-word-forward! editor)
            (ihx-apply-selection! document))))
    (\b
      "Select word backward" :scroll
      [state document editor caret]
      (dotimes [_ (get-prefix state)]
        (-> (ihx-selection document caret)
            (ihx-word-backward! editor)
            (ihx-apply-selection! document))))
    ((:or \j KeyEvent/VK_DOWN)
     "Move carets down" :scroll
     [state document caret]
     (dotimes [_ (get-prefix state)]
       (-> (ihx-selection document caret)
           (ihx-move-relative! :lines 1)
           ihx-shrink-selection
           (ihx-apply-selection-preserving document))))
    ((:or \k KeyEvent/VK_UP)
     "Move carets up" :scroll
     [state document caret]
     (dotimes [_ (get-prefix state)]
       (-> (ihx-selection document caret)
           (ihx-move-relative! :lines -1)
           ihx-shrink-selection
           (ihx-apply-selection-preserving document))))
    ((:or \h KeyEvent/VK_LEFT)
     "Move carets left" :scroll
     [state document caret]
     (-> (ihx-selection document caret)
         (ihx-move-backward (get-prefix state))
         ihx-shrink-selection
         (ihx-apply-selection! document)))
    ((:or \l KeyEvent/VK_RIGHT)
     "Move carets right" :scroll
     [state document caret]
     (-> (ihx-selection document caret)
         (ihx-move-forward (get-prefix state))
         ihx-shrink-selection
         (ihx-apply-selection! document)))
    ((:shift \G)
     "Move to line number" :scroll :jumplist-add
     [state editor document]
     (do (-> (ihx-move-caret-line-n editor document (get-prefix state))
             ihx-shrink-selection
             (ihx-apply-selection! document))
         (assoc state :mode :normal))))

  (:select
    (\g
      "Goto mode extending" :keep-prefix
      [state] (assoc state :mode :select-goto))
    (\v
      "Back to normal mode" [state] (assoc state :mode :normal))
    (\p
      "Paste" :undoable :write
      [project-state editor document]
      (paste-register (:registers project-state) editor document))
    (\w
      "Select word forward extending" :scroll
      [state document editor caret]
      (dotimes [_ (get-prefix state)]
        (-> (ihx-selection document caret)
            (ihx-word-forward-extending! editor)
            (ihx-apply-selection! document))))
    (\b
      "Select word backward extending" :scroll
      [state document editor caret]
      (dotimes [_ (get-prefix state)]
        (-> (ihx-selection document caret)
            (ihx-word-backward-extending! editor)
            (ihx-apply-selection! document))))
    ((:or \j KeyEvent/VK_DOWN)
     "Move carets down extending" :scroll
     [state document caret]
     (dotimes [_ (get-prefix state)]
       (-> (ihx-selection document caret)
           (ihx-move-relative! :lines 1)
           (ihx-apply-selection-preserving document))))
    ((:or \k KeyEvent/VK_UP)
     "Move carets up extending" :scroll
     [state document caret]
     (dotimes [_ (get-prefix state)]
       (-> (ihx-selection document caret)
           (ihx-move-relative! :lines -1)
           (ihx-apply-selection-preserving document))))
    ((:or \h KeyEvent/VK_LEFT)
     "Move carets left extending" :scroll
     [state document caret]
     (-> (ihx-selection document caret)
         (ihx-move-backward (get-prefix state))
         (ihx-apply-selection! document)))
    ((:or \l KeyEvent/VK_RIGHT)
     "Move carets right extending" :scroll
     [state document caret]
     (-> (ihx-selection document caret)
         (ihx-move-forward (get-prefix state))
         (ihx-apply-selection! document)))
    ((:shift \G)
     "Move to line number" :scroll :jumplist-add
     [state editor document]
     (do (-> (ihx-move-caret-line-n editor document (get-prefix state))
             (ihx-apply-selection! document))
         (assoc state :mode :select))))

  (:goto
    (Character/isDigit
      "Add prefix arg" :keep-prefix [char state] (update state :prefix conj char))
    (\d
      "Goto declaration" :jumplist-add
      [editor]
      (actions editor IdeActions/ACTION_GOTO_DECLARATION)
      [state] (assoc state :mode :normal))
    (\h
      "Move carets to line start" :scroll
      [editor document caret]
      (-> (ihx-selection document caret)
          (ihx-move-line-start editor document)
          ihx-shrink-selection
          (ihx-apply-selection! document))
      [state] (assoc state :mode :normal))
    (\l
      "Move carets to line end" :scroll
      [editor document caret]
      (-> (ihx-selection document caret)
          (ihx-move-line-end editor document)
          (ihx-move-backward 1)
          ihx-shrink-selection
          (ihx-apply-selection! document))
      [state] (assoc state :mode :normal))
    (\g
      "Move to line number" :scroll :jumplist-add
      [state editor document]
      (do (-> (ihx-move-caret-line-n editor document (get-prefix state))
              ihx-shrink-selection
              (ihx-apply-selection! document))
          (assoc state :mode :normal)))
    (\e
      "Move to file end" :scroll :jumplist-add
      [state editor document]
      (do (-> (ihx-move-file-end editor document)
              ihx-shrink-selection
              (ihx-apply-selection! document))
          (assoc state :mode :normal)))
    (_ [state] (assoc state :mode :normal)))

  (:select-goto
    (Character/isDigit
      "Add prefix arg" :keep-prefix [char state] (update state :prefix conj char))
    (\h
      "Move carets to line start extending" :scroll
      [editor document caret]
      (-> (ihx-selection document caret)
          (ihx-move-line-start editor document)
          (ihx-apply-selection! document))
      [state] (assoc state :mode :select))
    (\l
      "Move carets to line end extending" :scroll
      [editor document caret]
      (-> (ihx-selection document caret)
          (ihx-move-line-end editor document)
          (ihx-apply-selection! document))
      [state] (assoc state :mode :select))
    (\g
      "Move to line number" :scroll :jumplist-add
      [state editor document]
      (do (-> (ihx-move-caret-line-n editor document (get-prefix state))
              (ihx-apply-selection! document))
          (assoc state :mode :select)))
    (\e
      "Move to file end" :scroll :jumplist-add
      [state editor document]
      (do (-> (ihx-move-file-end editor document)
              (ihx-apply-selection! document))
          (assoc state :mode :select)))
    (_ [state] (assoc state :mode :select)))

  (:space
    (\f
      "File finder"
      [state project editor]
      (let [manager (.. SearchEverywhereManager (getInstance project))
            data-context (.getDataContext editor)
            action-event (AnActionEvent/createFromDataContext
                           ActionPlaces/KEYBOARD_SHORTCUT nil data-context)]
        (.show manager "FileSearchEverywhereContributor" nil action-event)
        (assoc state :mode :normal))))


  #_(:insert
      (_ [project-state] (assoc project-state :pass true))))


(defn handle-editor-event
  [project ^EditorImpl editor ^KeyEvent event]
  (let [project-state (get @state project)
        editor-state (or (get project-state editor) {:mode :normal})
        mode (:mode editor-state)
        debounce (:debounce editor-state)
        result-fn (partial editor-handler project project-state editor-state editor event)
        result
        (if (= mode :insert)
          (cond
            (= (.getKeyCode event) KeyEvent/VK_ESCAPE) (result-fn)
            (and debounce (= (.getID event) KeyEvent/KEY_TYPED))
            {editor (assoc editor-state :debounce false)}
            :else :pass)
          (if (= (.getID event) KeyEvent/KEY_PRESSED)
            (result-fn)
            nil))]
    (cond
      (= :pass result) false
      (map? result) (do
                      (.consume event)
                      (swap! state assoc project (deep-merge project-state result))
                      (ui/update-mode-panel! project (or (get-in @state [project editor])
                                                         {:mode :normal}))
                      true)
      :default (do
                 (.consume event)
                 true))))
