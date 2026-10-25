package com.wenjunhuang.codeepiphany.presentation.editor

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.wenjunhuang.codeepiphany.presentation.browser.PreviewEditor
import org.jdom.Element
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class DelegatingTextEditorTest {
    @Test fun fileAndAllJavaDefaultMethodsHaveExplicitForwarding() {
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
