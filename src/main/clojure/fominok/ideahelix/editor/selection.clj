;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.selection
  (:import (com.intellij.openapi.editor.impl CaretImpl)))

(defn ensure-selection
  "Keep at least one character selection"
  [caret]
  (let [offset (.getOffset caret)
        selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)]
    (when (= offset selection-end selection-start)
      (.setSelection caret offset (inc offset)))))

(defn flip-selection [caret]
  (let [selection-start (.getSelectionStart caret)]
    (if (= (.getOffset caret) selection-start)
      (.moveToOffset caret (dec (.getSelectionEnd caret)))
      (.moveToOffset caret selection-start))))

(defn ensure-selection-forward [caret]
  (let [selection-start (.getSelectionStart caret)]
    (when (= (.getOffset caret) selection-start)
      (.moveToOffset caret (dec (.getSelectionEnd caret))))))

(defn reversed? [caret]
  (let [selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)
        offset (.getOffset caret)]
    (and
      (= offset selection-start)
      (< selection-start (dec selection-end)))))

(defn degenerate? [caret]
  (let [selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)
        offset (.getOffset caret)]
    (and
      (= offset selection-start)
      (= offset (dec selection-end)))))


(defn extending
  "Executes function f on the caret but extending the existing selection or creating a new one"
  [caret f]
  (let [selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)
        previous-offset (.getOffset caret)
        degenerate (and
                     (= previous-offset selection-start)
                     (= previous-offset (dec selection-end)))
        reversed (and
                   (= previous-offset selection-start)
                   (< selection-start selection-end))
        _ (f caret)
        new-offset (.getOffset caret)
        move-right (> new-offset previous-offset)]

    (cond
      (and degenerate move-right) (.setSelection caret selection-start (inc new-offset))
      degenerate (.setSelection caret new-offset selection-end)
      reversed(.setSelection caret new-offset selection-end)
      :else (.setSelection caret selection-start (inc new-offset)))))

(defn select-lines
  [document ^CaretImpl caret & {:keys [extend] :or {extend false}}]
  (let [[selection-start selection-end] (sort [(.getSelectionStart caret)
                                               (.getSelectionEnd caret)])
        line-start (.getLineNumber document selection-start)
        line-end (.getLineNumber document selection-end)
        start (.getLineStartOffset document line-start)
        end (.getLineEndOffset document line-end)
        extend? (and extend (= selection-start start) (= selection-end end))
        adjusted-end (if extend?
                       (.getLineEndOffset
                         document
                         (min (inc line-end)
                              (dec (.getLineCount document))))
                       end)]
    (.setSelection caret start adjusted-end)
    (.moveToOffset caret adjusted-end)))