/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package fominok.ideahelix

import clojure.java.api.Clojure
import clojure.lang.IFn
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.ui.UIUtil
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

class Init : ProjectActivity {
    override suspend fun execute(project: Project) {
        val pushEvent: IFn
        val focusEditor: IFn

        val e: EditorImpl

        // Per https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html#using-serviceloader:
        val currentThread = Thread.currentThread()
        val originalClassLoader = currentThread.contextClassLoader
        val pluginClassLoader = javaClass.classLoader
        try {
            currentThread.contextClassLoader = pluginClassLoader

            val require = Clojure.`var`("clojure.core", "require")
            require.invoke(Clojure.read("fominok.ideahelix.core"))

            pushEvent = Clojure.`var`("fominok.ideahelix.core", "push-event") as IFn
            focusEditor = Clojure.`var`("fominok.ideahelix.core", "focus-editor") as IFn
        } finally {
            currentThread.contextClassLoader = originalClassLoader
        }

        val caster: EditorEventMulticasterEx = EditorFactory.getInstance().eventMulticaster as EditorEventMulticasterEx;
        caster.addFocusChangeListener(object : FocusChangeListener {
            override fun focusGained(editor: Editor) {
                super.focusGained(editor)
                focusEditor.invoke(project, editor)
            }
        }, project)

        IdeEventQueue.getInstance().addDispatcher({
            if (it is KeyEvent) {
                val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                pushEvent.invoke(project, focusOwner, it) as Boolean
            } else {
                false
            }
        }, project)
    }
}
