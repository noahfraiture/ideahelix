;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.keymap
  "Keymap definition utilities."
  (:require
    [clojure.spec.alpha :as s])
  (:import
    (com.intellij.openapi.command
      CommandProcessor
      WriteCommandAction)
    (com.intellij.openapi.command.impl
      FinishMarkAction
      StartMarkAction)
    (com.intellij.openapi.editor.impl
      EditorImpl)
    (com.intellij.openapi.project
      Project)
    (java.awt.event
      KeyEvent)))


;; Key matcher.
(s/def ::matcher-core
  (s/or :symbol symbol?
        :int int?
        :char char?))


;; Matcher with possible modifier key applied.
(s/def ::matcher
  (s/or :matcher-core ::matcher-core
        :with-ctrl (s/cat
                     :tag (partial = :ctrl)
                     :matcher-core ::matcher-core)
        :with-alt (s/cat
                    :tag (partial = :alt)
                    :matcher-core ::matcher-core)
        :or (s/cat
              :tag (partial = :or)
              :matchers (s/+ ::matcher))))


;; One of possible dependencies of the statement to expect.
(s/def ::dep
  (s/or :char (partial = 'char) ; typed character
        :editor (partial = 'editor) ; editor in focus
        :state (partial = 'state) ; ideahelix->project->editor state
        :document (partial = 'document) ; document instance running in the editor
        :caret (partial = 'caret) ; one caret, makes body applied to each one equally
        :project (partial = 'project)
        :write (partial = 'write))) ; wrap into "critical section" required on modifications)) ;; wraps body into a single undoable action


;; Statement to execute. If the statement is just a :pass keyword
;; it deserves a special treatment.
(s/def ::statement
  (s/or :pass (partial = :pass)
        :statement any?))


;; Similarly to function definitions, body is made of arguments vector
;; and a statement to execute.
(s/def ::body
  (s/cat
    :deps (s/spec (s/coll-of ::dep))
    :statement ::statement))


;; Key* to bodies to execute mapping, where input can be matched in several ways.
;; There are several bodies possible that will be executed sequentially with
;; their own dependencies each.
(s/def ::mapping
  (s/cat :matcher ::matcher
         :doc (s/? string?)
         :undoable (s/? (partial = :undoable))
         :bodies (s/+ ::body)))


(s/def ::mode-name
  (s/or :name keyword?
        :or (s/cat :tag (partial = :or)
                   :mode-names (s/+ keyword?))))


;; Top-level sections of `defkeymap` are grouped by Helix mode with key
;; mappings defined inside for each
(s/def ::mode
  (s/cat
    :mode ::mode-name
    :mappings (s/+ (s/spec ::mapping))))


;; The top level spec for `defkeymap` contents
(s/def ::defkeymap
  (s/coll-of ::mode))


(defn- process-single-matcher
  [matcher]
  "Matching process happens through a nested map attempting to do so with modifier,
  then matcher type and the actual matcher, and this function builds such a path of
  nested maps.
  It requires a bit of evaluation at macro expansion time to figure out the type of
  the symbol, because either it is a predicate and shall go as :fn type or evaluates
  into a value to compare key event with such as a key code."
  (let [modifier (get-in matcher [1 :tag])
        matcher-core (or (get-in matcher [1 :matcher-core 1])
                         (get-in matcher [1 1]))
        evaluated-matcher (if (= '_ matcher-core)
                            :any
                            (eval matcher-core))
        matcher-type (cond
                       (char? evaluated-matcher) :char
                       (instance? java.lang.Integer evaluated-matcher) :int
                       (fn? evaluated-matcher) :fn)]
    (if (= evaluated-matcher :any)
      [:any]
      [modifier matcher-type evaluated-matcher])))


(defn- process-matcher
  "process-single-matcher has the core logic, but first we handle (:or ...) construct"
  [matcher]
  (if (= (first matcher) :or)
    (let [matchers (get-in matcher [1 :matchers])]
      (map process-single-matcher matchers))
    [(process-single-matcher matcher)]))


(defn- process-body
  "Taking a dependencies vector and a statement, this function wraps the statement
  with requested dependencies with symbols linked.
  For `caret` dependency the body will be executed for each caret.
  For `write` dependency the `runWriteCommandAction` wrapper is used to comply with
  Idea's requirements."
  [project state editor event {:keys [deps statement]}]
  (let [deps-bindings-split (group-by #(#{:caret :write :undoable-action} (first %)) deps)
        deps-bindings-top
        (into [] (mapcat
                   (fn [[kw sym]]
                     [sym (case kw
                            :state state
                            :project project
                            :document `(.getDocument ~editor)
                            :char `(.getKeyChar ~event)
                            :editor editor)])
                   (get deps-bindings-split nil)))
        caret-sym (get-in deps-bindings-split [:caret 0 1])
        write-sym (get-in deps-bindings-split [:write 0 1])
        gen-statement
        (cond-> (second statement)
          caret-sym
          ((fn [s]
             `(let [caret-model# (.getCaretModel ~editor)]
                (.runForEachCaret
                  caret-model#
                  (fn [~caret-sym] ~s)))))
          write-sym
          ((fn [s]
             `(WriteCommandAction/runWriteCommandAction
                ~project
                (fn [] ~s)))))]
    `(let ~deps-bindings-top
       ~gen-statement)))


(defn- process-bodies
  "Builds handler function made of sequentially executed statements with dependencies
  injected for each."
  [bodies undoable doc]
  (let [project (gensym "project")
        state (gensym "state")
        editor (gensym "editor")
        event (gensym "event")
        bodies (map (partial process-body project state editor event) bodies)
        docstring (or (str "IHx: " doc) "IdeaHelix command")
        statement
        (if undoable
          `(let [return# (volatile! nil)]
             (.. CommandProcessor getInstance
                 (executeCommand
                   ~project
                   (fn []
                     (let [start# (StartMarkAction/start ~editor ~project ~docstring)]
                       (vreset! return# (do ~@bodies))
                       (FinishMarkAction/finish ~project ~editor start#)))
                   ~docstring
                   nil))
             @return#)
          `(do ~@bodies))]
    `(fn [^Project ~project ~state ^EditorImpl ~editor ^KeyEvent ~event]
       ~statement)))


(defn- process-mappings
  "Inserts into mode mapping a handler function (sexp)
  under a matcher path ([mode literal-type/fn literal-value/predicate])."
  [mappings]
  (reduce
    (fn [acc m]
      (let [bodies (process-bodies (:bodies m) (:undoable m) (:doc m))
            match-paths (process-matcher (:matcher m))]
        (reduce (fn [a p] (assoc-in a p bodies)) acc match-paths)))

    {}
    mappings))


(defn- flatten-modes
  [acc {:keys [mode mappings]}]
  (case (first mode)
    :name (update acc (second mode) (fnil concat []) mappings)
    :or (reduce
          (fn [inner-acc mode-name]
            (update inner-acc mode-name (fnil concat []) mappings)) acc (get-in mode [1 :mode-names]))))


(defmacro defkeymap
  "Define a keymap function that matches a key event by set of rules and handles it."
  [ident & mappings]
  (let [rules
        (as-> (s/conform ::defkeymap mappings) $
              (reduce flatten-modes {} $)
              (update-vals $ process-mappings))]
    `(defn ~ident [project# state# ^EditorImpl editor# ^KeyEvent event#]
       (let [modifier# (or (when (.isControlDown event#) :ctrl)
                           (when (.isAltDown event#) :alt))
             mode# (:mode state#)
             any-mode-matchers# (get-in ~rules [:any modifier#])
             cur-mode-matchers# (get-in ~rules [mode# modifier#])
             find-matcher#
             (fn [in-mode#]
               (or (get-in in-mode# [:int (.getKeyCode event#)])
                   (get-in in-mode# [:char (.getKeyChar event#)])
                   (some
                     (fn [[pred# f#]] (when (pred# (.getKeyChar event#)) f#))
                     (get in-mode# :fn))))
             handler-opt#
             (or (find-matcher# any-mode-matchers#)
                 (find-matcher# cur-mode-matchers#)
                 (get-in ~rules [mode# :any]))]
         (if-let [handler# handler-opt#]
           (handler# project# state# editor# event#))))))
