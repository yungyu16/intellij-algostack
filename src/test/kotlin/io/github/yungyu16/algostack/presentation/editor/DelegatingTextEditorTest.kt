package io.github.yungyu16.algostack.presentation.editor

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import io.github.yungyu16.algostack.presentation.browser.PreviewEditor
import org.jdom.Element
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

class DelegatingTextEditorTest {
    @Test fun fileEditorDefaultMethodsHaveExplicitForwarding() {
        val source = LightVirtualFile("solution.java")
        val calls = arrayListOf<String>()
        val delegate = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TextEditor::class.java)) { _, method, args ->
            calls += "${method.name}/${args?.size ?: 0}"
            when (method.name) {
                "getState" -> FileEditorState.INSTANCE
                "getFilesToRefresh" -> listOf(source)
                "isValid" -> true
                "isModified" -> false
                else -> null
            }
        } as TextEditor
        val editor: FileEditor = DelegatingTextEditor(delegate, source)
        assertSame(source, editor.file)
        assertFalse(calls.any { it.startsWith("getFile/") })
        assertSame(FileEditorState.INSTANCE, editor.getState(FileEditorStateLevel.FULL))
        editor.setState(FileEditorState.INSTANCE, true)
        editor.selectNotify(); editor.deselectNotify()
        assertNull(editor.backgroundHighlighter)
        assertNull(editor.currentLocation)
        assertNull(editor.structureViewBuilder)
        assertEquals(listOf(source), editor.filesToRefresh)
        assertNull(editor.tabActions)
        assertTrue(editor.isValid)
        for (expected in listOf("getState/1", "setState/2", "selectNotify/0", "deselectNotify/0", "getBackgroundHighlighter/0",
            "getCurrentLocation/0", "getStructureViewBuilder/0", "getFilesToRefresh/0", "getTabActions/0", "isValid/0"))
            assertTrue("Missing forwarding: $expected", expected in calls)
    }

    @Test fun loadedStateFollowsTheDelegateInsteadOfTheInterfaceDefault() {
        var loaded = false
        val delegate = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TextEditor::class.java)) { _, method, _ ->
            check(method.name == "isEditorLoaded")
            loaded
        } as TextEditor
        val editor = DelegatingTextEditor(delegate, LightVirtualFile("solution.java"))
        assertFalse(editor.isEditorLoaded)
        loaded = true
        assertTrue(editor.isEditorLoaded)
        loaded = false
        assertFalse(editor.isEditorLoaded)
    }

    @Test fun propertyEventsUseWrapperIdentityAndListenersCanBeRemovedIndividually() {
        lateinit var support: PropertyChangeSupport
        val delegate = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TextEditor::class.java)) { proxy, method, args ->
            when (method.name) {
                "addPropertyChangeListener" -> { support.addPropertyChangeListener(args!![0] as PropertyChangeListener); null }
                "removePropertyChangeListener" -> { support.removePropertyChangeListener(args!![0] as PropertyChangeListener); null }
                "toString" -> "DelegateTextEditor"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args!![0]
                else -> error("Unexpected call: ${method.name}")
            }
        } as TextEditor
        support = PropertyChangeSupport(delegate)
        val editor = DelegatingTextEditor(delegate, LightVirtualFile("solution.java"))
        val events = mutableListOf<PropertyChangeEvent>()
        val listener = PropertyChangeListener { events += it }
        val otherEvents = mutableListOf<PropertyChangeEvent>()
        val other = PropertyChangeListener { otherEvents += it }
        val original = PropertyChangeEvent(delegate, FileEditor.getPropModified(), false, true).apply {
            propagationId = "origin"
        }
        editor.addPropertyChangeListener(listener)
        editor.addPropertyChangeListener(other)
        support.firePropertyChange(original)
        assertSame(editor, events.single().source)
        assertSame(editor, otherEvents.single().source)
        assertEquals(FileEditor.getPropModified(), events.single().propertyName)
        assertEquals(false, events.single().oldValue)
        assertEquals(true, events.single().newValue)
        assertEquals("origin", events.single().propagationId)
        assertSame(delegate, original.source)

        // 重复注册保留两份通知，移除一次只取消一次注册。
        editor.addPropertyChangeListener(listener)
        events.clear()
        support.firePropertyChange(FileEditor.getPropValid(), true, false)
        assertEquals(2, events.size)
        assertTrue(events.all { it.source === editor && it.propertyName == FileEditor.getPropValid() })
        editor.removePropertyChangeListener(listener)
        events.clear()
        support.firePropertyChange(original)
        assertEquals(1, events.size)
        editor.removePropertyChangeListener(listener)
        editor.removePropertyChangeListener(listener)
        events.clear()
        otherEvents.clear()
        support.firePropertyChange(original)
        assertTrue(events.isEmpty())
        assertEquals(1, otherEvents.size)
        editor.removePropertyChangeListener(other)
        assertEquals(0, support.propertyChangeListeners.size)
    }

    @Test fun customEditorsAndTextProvidersDoNotFallBackToDeprecatedOrEmptyDefaults() {
        for (type in listOf(ChallengeEditor::class.java, PreviewEditor::class.java)) {
            val method = type.getMethod("getFile")
            assertFalse(method.declaringClass.isInterface)
            assertEquals(VirtualFile::class.java, method.returnType)
        }
        for (type in listOf(ChallengeEditorProvider::class.java)) {
            assertEquals(type, type.getMethod("readState", Element::class.java, Project::class.java, VirtualFile::class.java).declaringClass)
            assertEquals(type, type.getMethod("writeState", FileEditorState::class.java, Project::class.java, Element::class.java).declaringClass)
        }
    }
}
