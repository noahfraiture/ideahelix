;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.movement
  "Movements that aren't defined by existing Idea actions")

(defn move-caret-line-start [document caret]
  (let [offset (.getLineStartOffset document (.. caret getLogicalPosition line))]
    (.moveToOffset caret offset)
    (.setSelection caret offset (inc offset))))

(defn move-caret-line-end [document caret]
  (let [offset (.getLineEndOffset document (.. caret getLogicalPosition line))]
    (.moveToOffset caret offset)
    (.setSelection caret offset (inc offset))))

(defn move-caret-left [caret]
  (let [offset (max 0 (dec (.getOffset caret)))]
    (.moveToOffset caret offset)
    (.setSelection caret offset (inc offset))))

(defn move-caret-right [document caret]
  (let [offset (min (.getTextLength document) (inc (.getOffset caret)))]
    (.moveToOffset caret offset)
    (.setSelection caret offset (inc offset))))

(defn move-caret-line-n [document caret])