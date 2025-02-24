;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns fominok.ideahelix.core
  (:require [nrepl.server :refer [start-server]]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [fominok.ideahelix.editor :refer [handle-editor-event]])
  (:import (com.intellij.openapi.editor.impl EditorComponentImpl)))

(set! *warn-on-reflection* true)

(defn push-event [project focus-owner event]
  (condp instance? focus-owner
    EditorComponentImpl
    (handle-editor-event project (.getEditor ^EditorComponentImpl focus-owner) event)

    false))

(defonce -server (start-server :port 7888 :handler cider-nrepl-handler))
