(ns fominok.ideahelix.editor
  (:require [clojure.string :as str]
            [fominok.ideahelix.keymap :refer [defkeymap]])
  (:import
    (com.intellij.openapi.actionSystem ActionManager ActionPlaces AnActionEvent)
    (com.intellij.openapi.editor CaretVisualAttributes CaretVisualAttributes$Weight EditorCustomElementRenderer)
    (com.intellij.openapi.editor.event CaretListener)
    (com.intellij.openapi.editor.impl EditorImpl)
    (com.intellij.openapi.wm WindowManager)
    (com.intellij.ui JBColor)
    (fominok.ideahelix ModePanel)
    (java.awt.event KeyEvent)))


;; We're allowed to use "thread-unsafe" mutable state since events are coming within 1 thread
;; which is blocked until it is decided what to do with a keyboard event
(defonce state {})

(defn update-mode-panel! [project mode]
  (let [id (ModePanel/ID)
        status-bar (.. WindowManager getInstance (getStatusBar project))
        widget (.getWidget status-bar id)]
    (.setText widget (str/upper-case (name mode)))
    (.updateWidget status-bar id)))

(defn set-mode! [project mode]
  (update-mode-panel! project mode)
  (alter-var-root #'state assoc-in[project :mode] mode)
  :consume)

(defn action [^EditorImpl editor action-name]
  (let [data-context (.getDataContext editor)
        action (.getAction (ActionManager/getInstance) action-name)]
    (.actionPerformed
      action
      (AnActionEvent/createFromDataContext
        ActionPlaces/KEYBOARD_SHORTCUT nil data-context))))


(defn move-caret-line-start [document caret]
  (let [offset (.getLineStartOffset document (.. caret getLogicalPosition line))]
    (.moveToOffset caret offset)))

(defn move-caret-line-end [document caret]
  (let [offset (.getLineEndOffset document (.. caret getLogicalPosition line))]
    (.moveToOffset caret offset)))

(defn move-caret-line-n [document caret])

(defn set-mode [state mode]
  (assoc state :mode mode :prefix []))

(defkeymap
  editor-handler
  (:any
    (KeyEvent/VK_ESCAPE [state] (set-mode state :normal))
    (KeyEvent/VK_SHIFT [] :pass))
  (:normal
    (Character/isDigit [char state] (update state :prefix conj char))
    (\i [state] (set-mode state :insert))
    (\g [state] (set-mode state :goto))
    (\j [editor] (action editor "EditorDown"))
    (\k [editor] (action editor "EditorUp"))
    (\h [editor] (action editor "EditorLeft"))
    (\l [editor] (action editor "EditorRight")))
  (:goto
    (Character/isDigit [char state] (update state :prefix conj char))
    (\h
      [document caret] (move-caret-line-start document caret)
      [state] (set-mode state :normal))
    (\l
      [document caret] (move-caret-line-end document caret)
      [state] (set-mode state :normal))
    (\g
      [document caret] (move-caret-line-n document caret)
      [state] (set-mode state :normal))
    (\s
      [editor] (action editor "EditorLineStart")
      [state] (set-mode state :normal)))
  (:insert
    ((:ctrl \a) [document caret] (move-caret-line-start document caret))
    ((:ctrl \e) [document caret] (move-caret-line-end document caret))
    ((:ctrl \u0001) [document caret] (move-caret-line-start document caret))
    ((:ctrl \u0005) [document caret] (move-caret-line-end document caret))
    (_ [] :pass)))

(defn highlight-primary-caret [editor event]
  (let [primary-caret (.. editor getCaretModel getPrimaryCaret)
        primary-attributes
        (CaretVisualAttributes. JBColor/GRAY CaretVisualAttributes$Weight/HEAVY)
        secondary-attributes
        (CaretVisualAttributes. JBColor/BLACK CaretVisualAttributes$Weight/HEAVY)
        caret (.getCaret event)]
    (.setVisualAttributes caret
                          (if (= caret primary-caret)
                            primary-attributes
                            secondary-attributes))))

(defn caret-listener [editor]
  (reify CaretListener
    (caretPositionChanged [_ event]
      (highlight-primary-caret editor event))))

(defn handle-editor-event [project ^EditorImpl editor ^KeyEvent event]
  (let [proj-state (get state project)
        result (editor-handler proj-state editor event)]
    (when-not (get-in proj-state [editor :caret-listener])
      (let [listener (caret-listener editor)]
        (.. editor getCaretModel (addCaretListener listener))
        (alter-var-root #'state assoc-in [project editor :caret-listener] listener)))
    (cond
      (nil? result) (do
                      (.consume event)
                      true)
      (= :pass result) false
      (map? result) (do
                      (.consume event)
                      (when (not= (:mode result) (:mode proj-state))
                        (update-mode-panel! project (:mode result)))
                      (alter-var-root #'state assoc project result)
                      true))))

