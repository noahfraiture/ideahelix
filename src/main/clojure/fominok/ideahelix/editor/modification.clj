;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.modification
  (:require [fominok.ideahelix.editor.selection :refer [ensure-selection]]))

(defn into-insert-mode [caret]
  (let [selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)]
    (.moveCaretRelatively caret 1 0 true false)
    (.setSelection caret selection-start selection-end)))

(defn into-normal-mode [caret]
  (if (.hasSelection caret)
    (.moveToOffset caret (dec (.getOffset caret)))
    (ensure-selection caret)))

(defn backspace [document caret]
  (let [offset (.getOffset caret)]
    (.deleteString document (dec offset) offset)))

(defn insert-char [document caret char]
  (when-not (Character/isISOControl char)
    (.insertString document (.getOffset caret) (str char))
    (let [selection-start (.getSelectionStart caret)]
      (.moveCaretRelatively caret 1 0 false false)
      (.setSelection caret selection-start (.getOffset caret)))))
