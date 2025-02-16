(ns fominok.ideahelix.core
  (:require [nrepl.server :refer [start-server]]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [fominok.ideahelix.editor :refer [handle-editor-event set-mode!]])
  (:import (com.intellij.openapi.editor.impl EditorComponentImpl)))

(set! *warn-on-reflection* true)

(defonce -server (start-server :port 7888 :handler cider-nrepl-handler))

(defn push-event [project ^java.awt.Component focus-owner event]
  (condp instance? focus-owner
    EditorComponentImpl
    (handle-editor-event project (.getEditor ^EditorComponentImpl focus-owner) event)

    false))

(defn init-project [project]
  (set-mode! project :normal))