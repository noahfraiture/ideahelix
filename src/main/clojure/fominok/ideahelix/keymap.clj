(ns fominok.ideahelix.keymap
  "Keymap definition utilities."
  (:require [clojure.spec.alpha :as s])
  (:import (com.intellij.openapi.editor.impl EditorImpl)
           (java.awt.event KeyEvent)))

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
  (s/or :char (partial = 'char)
        :editor (partial = 'editor)
        :state (partial = 'state)
        :document (partial = 'document)
        :caret (partial = 'caret)))

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
         :bodies (s/+ ::body)))

;; Top-level sections of `defkeymap` are grouped by Helix mode with key
;; mappings defined inside for each
(s/def ::mode
  (s/cat
    :mode keyword?
    :mappings (s/+ (s/spec ::mapping))))

;; The top level spec for `defkeymap` contents
(s/def ::defkeymap
  (s/coll-of ::mode))

(defn- process-single-matcher [matcher]
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
  with requested dependencies with symbols linked."
  [state editor event {:keys [deps statement]}]
  (let [deps-bindings-split (group-by #(= :caret (first %)) deps)
        deps-bindings-top
        (into [] (mapcat
                   (fn [[kw sym]]
                     [sym (case kw
                            :state state
                            :document `(.getDocument ~editor)
                            :char `(.getKeyChar ~event)
                            :editor editor)])
                   (get deps-bindings-split false)))
        caret-sym (get-in deps-bindings-split [true 0 1])
        statement (second statement)
        gen-statement (if caret-sym
                        `(let [caret-model# (.getCaretModel ~editor)]
                           (.runForEachCaret
                             caret-model#
                             (fn [~caret-sym] ~statement)))
                        statement)]
    `(let ~deps-bindings-top
       ~gen-statement)))

(defn- process-bodies
  "Builds handler function made of sequentially executed statements with dependencies
  injected for each."
  [bodies]
  (let [state (gensym "state")
        editor (gensym "editor")
        event (gensym "event")
        bodies (map (partial process-body state editor event) bodies)]
    `(fn [~state ^EditorImpl ~editor ^KeyEvent ~event]
       (do ~@bodies))))

(defn- process-mappings
  "Inserts into mode mapping a handler function (sexp)
  under a matcher path ([mode literal-type/fn literal-value/predicate])."
  [mappings]
  (reduce
    (fn [acc m]
      (let [bodies (process-bodies (:bodies m))
            match-paths (process-matcher (:matcher m))]
        (reduce (fn [a p] (assoc-in a p bodies)) acc match-paths)))

    {}
    mappings))

(defmacro defkeymap
  "Define a keymap function that matches a key event by set of rules and handles it."
  [ident & mappings]
  (let [rules (as-> (s/conform ::defkeymap mappings) $
                    (group-by :mode $)
                    (update-vals $ first)
                    (update-vals $ #(-> % :mappings process-mappings)))]
    `(defn ~ident [state# ^EditorImpl editor# ^KeyEvent event#]
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
           (handler# state# editor# event#))))))
