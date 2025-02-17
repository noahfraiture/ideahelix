;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.selection
  (:import (com.intellij.openapi.editor.impl CaretImpl EditorImpl)))

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