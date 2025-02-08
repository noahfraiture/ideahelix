(ns fominok.ideahelix.core
  (:require [nrepl.server :refer [start-server]]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [fominok.ideahelix.editor :refer [handle-editor-event set-mode!]])
  (:import (com.intellij.openapi.editor.impl EditorComponentImpl)))

(defonce -server (start-server :port 7888 :handler cider-nrepl-handler))

(defn push-event [project focus-owner event]
  (condp instance? focus-owner
    EditorComponentImpl (handle-editor-event project event)
    nil))

(defn init-project [project]
  (set-mode! project :normal))