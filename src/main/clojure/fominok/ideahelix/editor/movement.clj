;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.movement
  "Movements that aren't defined by existing Idea actions"
  (:require [fominok.ideahelix.editor.selection :refer [ensure-selection]]))

(defn move-caret-line-start [document caret]
  (let [offset (.getLineStartOffset document (.. caret getLogicalPosition line))]
    (.moveToOffset caret offset)
    (ensure-selection caret)))

(defn move-caret-line-end [document caret]
  (let [offset (.getLineEndOffset document (.. caret getLogicalPosition line))]
    (.moveToOffset caret offset)
    (ensure-selection caret)))

(defn move-caret-left [caret]
  (.moveCaretRelatively caret -1 0 false false)
  (ensure-selection caret))

(defn move-caret-right [caret]
  (.moveCaretRelatively caret 1 0 false false)
  (ensure-selection caret))

(defn move-caret-down [caret]
  (.moveCaretRelatively caret 0 1 false false)
  (ensure-selection caret))

(defn move-caret-up [caret]
  (.moveCaretRelatively caret 0 -1 false false)
  (ensure-selection caret))

(defn move-caret-line-n [document caret])