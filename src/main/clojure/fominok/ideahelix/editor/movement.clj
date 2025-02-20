;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.movement
  "Movements that aren't defined by existing Idea actions"
  (:require
    [fominok.ideahelix.editor.util
     :refer [inc-within-bounds dec-within-bounds]
     :rename {inc-within-bounds binc dec-within-bounds bdec}]
    [fominok.ideahelix.editor.selection :refer [ensure-selection reversed?]])
  (:import (com.intellij.openapi.editor ScrollType)
           (com.intellij.openapi.editor.actions CaretStopPolicy EditorActionUtil)))

(defn scroll-to-primary-caret [editor]
  (.. editor getScrollingModel (scrollToCaret ScrollType/RELATIVE)))

(defn move-caret-line-start [document caret]
  (let [offset (.getLineStartOffset document (.. caret getLogicalPosition line))]
    (.moveToOffset caret offset)
    (ensure-selection document caret)))

(defn move-caret-line-end [document caret]
  (let [offset (.getLineEndOffset document (.. caret getLogicalPosition line))]
    (.moveToOffset caret (bdec offset))
    (ensure-selection document caret)))

(defn move-caret-backward [document caret]
  (.moveCaretRelatively caret -1 0 false false)
  (ensure-selection document caret))

(defn move-caret-forward [document caret]
  (.moveCaretRelatively caret 1 0 false false)
  (ensure-selection document caret))

(defn move-caret-down [document caret]
  (.moveCaretRelatively caret 0 1 false false)
  (ensure-selection document caret))

(defn move-caret-up [document caret]
  (.moveCaretRelatively caret 0 -1 false false)
  (ensure-selection document caret))

(defn move-caret-word-forward [editor caret]
  (let [document (.getDocument editor)
        prev-offset (.getOffset caret)]
    (EditorActionUtil/moveToNextCaretStop editor CaretStopPolicy/WORD_START false true)
    (if (= prev-offset (dec (.getOffset caret)))
      (do
        (EditorActionUtil/moveToNextCaretStop editor CaretStopPolicy/WORD_START false true)
        (let [offset (.getOffset caret)]
          (.moveCaretRelatively caret -1 0 false false)
          (.setSelection caret (binc document prev-offset) offset)))
      (do
        (.setSelection caret prev-offset (.getOffset caret))
        (.moveToOffset caret (bdec (.getOffset caret)))))))

(defn move-caret-word-backward [editor caret]
  (let [offset (.getOffset caret)
        prev-offset (if (reversed? caret) offset (inc offset))]
    (EditorActionUtil/moveToPreviousCaretStop editor CaretStopPolicy/WORD_START false true)
    (.setSelection caret (.getOffset caret) prev-offset)))

(defn move-caret-line-n [document caret])