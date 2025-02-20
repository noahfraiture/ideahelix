;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.movement
  "Movements that aren't defined by existing Idea actions"
  (:require
    [fominok.ideahelix.editor.selection :refer [ensure-selection reversed? degenerate?]])
  (:import (com.intellij.openapi.editor ScrollType)
           (com.intellij.openapi.editor.actions EditorActionUtil)))

(defn scroll-to-primary-caret [editor]
  (.. editor getScrollingModel (scrollToCaret ScrollType/RELATIVE)))

(defn move-caret-line-start [document caret]
  (let [offset (.getLineStartOffset document (.. caret getLogicalPosition line))]
    (.moveToOffset caret offset)
    (ensure-selection caret)))

(defn move-caret-line-end [document caret]
  (let [offset (.getLineEndOffset document (.. caret getLogicalPosition line))]
    (.moveToOffset caret offset)
    (ensure-selection caret)))

(defn move-caret-backward [caret]
  (.moveCaretRelatively caret -1 0 false false)
  (ensure-selection caret))

(defn move-caret-forward [caret]
  (.moveCaretRelatively caret 1 0 false false)
  (ensure-selection caret))

(defn move-caret-down [caret]
  (.moveCaretRelatively caret 0 1 false false)
  (ensure-selection caret))

(defn move-caret-up [caret]
  (.moveCaretRelatively caret 0 -1 false false)
  (ensure-selection caret))

(defn move-caret-word-forward [document editor caret]
  (let [selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)
        reversed (reversed? caret)
        degenerate (degenerate? caret)]
    (EditorActionUtil/moveCaretToNextWord editor false true)
    (if reversed
      (.setSelection caret selection-start selection-end)
      (.setSelection caret
                     (if degenerate selection-start selection-end)
                     (inc (.getOffset caret))))))

(defn move-caret-word-backward [document editor caret]
  (let [selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)
        reversed (reversed? caret)
        degenerate (degenerate? caret)]
    (EditorActionUtil/moveCaretToPreviousWord editor false true)
    (if reversed
      (.setSelection caret (.getOffset caret) selection-start)
      (.setSelection caret (if degenerate
                             (.getOffset caret)
                             selection-start) selection-end))))

(defn move-caret-line-n [document caret])