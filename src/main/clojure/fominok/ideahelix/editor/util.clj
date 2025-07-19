;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.editor.util)


(defmacro when-let*
  ([bindings & body]
   (if (seq bindings)
     `(when-let [~(first bindings) ~(second bindings)]
        (when-let* ~(drop 2 bindings) ~@body))
     `(do ~@body))))


(defn deep-merge
  [& maps]
  (reduce (fn [m1 m2]
            (merge-with (fn [v1 v2]
                          (if (and (map? v1) (map? v2))
                            (deep-merge v1 v2)
                            v2))
                        m1 m2))
          maps))


(defn get-caret-contents
  [document caret]
  (.getText document (.getSelectionRange caret)))


(defn get-editor-height
  [editor]
  (let [editor-height-px (.. editor getScrollingModel getVisibleArea getHeight)
        line-height-px (.getLineHeight editor)]
    (int (quot editor-height-px line-height-px))))


(defn printable-char?
  [c]
  (let [block (java.lang.Character$UnicodeBlock/of c)]
    (and (not (Character/isISOControl c))
         (not= c java.awt.event.KeyEvent/CHAR_UNDEFINED)
         (some? block)
         (not= block java.lang.Character$UnicodeBlock/SPECIALS))))
