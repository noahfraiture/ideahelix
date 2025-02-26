;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.registers
  (:require
    [fominok.ideahelix.editor.selection :refer [reversed?]]
    [fominok.ideahelix.editor.util :refer [for-each-caret]]))


(defn copy-to-register
  [registers editor document & {:keys [register] :or {register \"}}]
  (let [model (.getCaretModel editor)
        strings
        (let [strings* (transient [])]
          (.runForEachCaret
            model
            (fn [caret] (conj! strings* (.getText document (.getSelectionRange caret)))))
          (persistent! strings*))]
    (assoc registers register strings)))


(defn paste-register
  [registers editor document & {:keys [register select] :or {register \" select false}}]
  (let [register-contents (get registers register)
        strings (concat register-contents (some-> (last register-contents) repeat))]
    (when (not (empty? strings))
      (with-local-vars [i 0]
        (for-each-caret
          editor
          (fn [caret]
            (let [string (nth strings @i)
                  pos (.getSelectionEnd caret)]
              (.insertString document pos string)
              (var-set i (inc @i))
              (when select
                (let [reversed (reversed? caret)
                      end (+ pos (count string))]
                  (.setSelection caret pos end)
                  (if reversed
                    (.moveToOffset caret pos)
                    (.moveToOffset caret (dec end))))))))))))
