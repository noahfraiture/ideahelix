;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.selection
  (:require
    [fominok.ideahelix.editor.util
     :refer [inc-within-bounds dec-within-bounds]
     :rename {inc-within-bounds binc dec-within-bounds bdec}])
  (:import
    (com.intellij.openapi.editor
      VisualPosition)
    (com.intellij.openapi.editor.impl
      CaretImpl)))


(defn ensure-selection
  "Keep at least one character selection"
  [document caret]
  (let [offset (.getOffset caret)
        selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)]
    (when-not (and (.hasSelection caret)
                   (or (= offset selection-start) (= offset (bdec selection-end))))
      (.setSelection caret offset (binc document offset)))))


(defn flip-selection
  [caret]
  (let [selection-start (.getSelectionStart caret)]
    (if (= (.getOffset caret) selection-start)
      (.moveToOffset caret (bdec (.getSelectionEnd caret)))
      (.moveToOffset caret selection-start))))


(defn ensure-selection-forward
  [caret]
  (let [selection-start (.getSelectionStart caret)]
    (when (= (.getOffset caret) selection-start)
      (.moveToOffset caret (bdec (.getSelectionEnd caret))))))


(defn shrink-selection
  [document caret]
  (let [offset (.getOffset caret)]
    (.setSelection caret offset (binc document offset))))


(defn keep-primary-selection
  [editor]
  (.. editor getCaretModel removeSecondaryCarets))


(defn reversed?
  [caret]
  (let [selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)
        offset (.getOffset caret)]
    (and
      (= offset selection-start)
      (< selection-start (bdec selection-end)))))


(defn degenerate?
  [caret]
  (let [selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)
        offset (.getOffset caret)]
    (and
      (= offset selection-start)
      (= offset (bdec selection-end)))))


(defn extending
  "Executes function f on the caret but extending the existing selection or creating a new one"
  ([document caret f]
   (let [selection-start (.getSelectionStart caret)
         selection-end (.getSelectionEnd caret)
         previous-offset (.getOffset caret)
         degenerate (and
                      (= previous-offset selection-start)
                      (= previous-offset (bdec selection-end)))
         reversed (and
                    (= previous-offset selection-start)
                    (< selection-start selection-end))
         _ (f caret)
         new-offset (.getOffset caret)
         move-right (> new-offset previous-offset)]

     (cond
       (and degenerate move-right) (.setSelection caret selection-start (binc document new-offset))
       degenerate (.setSelection caret new-offset selection-end)
       reversed (.setSelection caret new-offset selection-end)
       :else (.setSelection caret selection-start (binc document new-offset))))))


(defn select-lines
  [document ^CaretImpl caret & {:keys [extend] :or {extend false}}]
  (let [selection-start (.getSelectionStart caret)
        selection-end (.getSelectionEnd caret)
        line-start (.getLineNumber document selection-start)
        line-end (.getLineNumber document selection-end)
        start (.getLineStartOffset document line-start)
        end (.getLineEndOffset document line-end)
        extend? (and extend (= selection-start start) (= selection-end end))
        adjusted-end (if extend?
                       (binc document (.getLineEndOffset
                                        document
                                        (min (inc line-end)
                                             (dec (.getLineCount document)))))
                       (binc document end))]
    (.setSelection caret start adjusted-end)
    (.moveToOffset caret adjusted-end)))


(defn- line-length
  [document n]
  (let [start-offset (.getLineStartOffset document n)
        end-offset (.getLineEndOffset document n)]
    (- end-offset start-offset)))


(defn- scan-next-selection-placement
  [document height start end lines-count]
  (let [start-column (.column start)
        end-column (.column end)]
    (loop [line (+ height 1 (.line start))]
      (let [line-end (+ line height)]
        (when-not (>= line-end lines-count)
          (let [start-line-length (line-length document line)
                end-line-length (line-length document line-end)]
            (if (and (<= start-column start-line-length)
                     (<= end-column end-line-length))
              [line line-end]
              (recur (inc line)))))))))


(defn add-selection-below
  [editor caret]
  (let [model (.getCaretModel editor)
        document (.getDocument editor)
        selection-start (.offsetToLogicalPosition editor (.getSelectionStart caret))
        selection-end (.offsetToLogicalPosition editor (.getSelectionEnd caret))
        caret-col (.column (.offsetToLogicalPosition editor (.getOffset caret)))
        height (- (.line selection-end)
                  (.line selection-start))
        line-count (.. editor getDocument getLineCount)
        reversed (reversed? caret)]
    (when-let [[next-line-start next-line-end]
               (scan-next-selection-placement document height selection-start selection-end line-count)]
      (some-> (.addCaret model (VisualPosition.
                                 (if reversed
                                   next-line-start
                                   next-line-end) caret-col))
              (.setSelection (+ (.getLineStartOffset document next-line-start)
                                (.column selection-start))
                             (+ (.getLineStartOffset document next-line-end)
                                (.column selection-end)))))))
