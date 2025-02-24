;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.util)


(defn inc-within-bounds
  [document n]
  (min (dec (.getTextLength document)) (inc n)))


(defn dec-within-bounds
  [n]
  (max 0 (dec n)))


(defn for-each-caret
  [editor f]
  (let [model (.getCaretModel editor)]
    (.runForEachCaret model f)))
