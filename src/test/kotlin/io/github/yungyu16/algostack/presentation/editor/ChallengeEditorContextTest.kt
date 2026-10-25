package io.github.yungyu16.algostack.presentation.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestApplicationManager
import io.github.yungyu16.algostack.model.Actions
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.lang.reflect.Proxy
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ChallengeEditorContextTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun initializeApplication() {
            TestApplicationManager.getInstance()
        }
    }

    @Test
    fun floatingToolbarReceivesItsOwnEditorAndEnablesSurroundForSelection() {
        SwingUtilities.invokeAndWait {
            val project = ProjectManager.getInstance().defaultProject
            val file = LightVirtualFile("solution.java")
            var selected = false
            val selection = proxy<SelectionModel> { name, _ -> if (name == "hasSelection") selected else null }
            lateinit var editor: Editor
            val content = object : JPanel(), UiDataProvider {
                override fun uiDataSnapshot(sink: DataSink) {
                    sink[CommonDataKeys.EDITOR] = editor
                }
            }
            editor = proxy { name, _ ->
                when (name) {
                    "getSelectionModel" -> selection
                    "getContentComponent", "getComponent" -> content
                    "getProject" -> project
                    else -> null
                }
            }
            val data = UserDataHolderBase()
            val delegate = proxy<TextEditor> { name, args ->
                when (name) {
                    "getEditor" -> editor
                    "getComponent" -> content
                    "getUserData" -> data.getUserData(args!![0] as com.intellij.openapi.util.Key<Any>)
                    "putUserData" -> {
                        data.putUserData(args!![0] as com.intellij.openapi.util.Key<Any>, args[1]); null
                    }

                    else -> null
                }
            }
            val manager = ActionManager.getInstance()
            val registered = manager.getAction(Actions.CHALLENGE_EDITOR_TOOLBAR_GROUP) == null
            if (registered) manager.registerAction(Actions.CHALLENGE_EDITOR_TOOLBAR_GROUP, DefaultActionGroup())
            val wrapper = ChallengeEditor(delegate, project, file)
            try {
                val pane = wrapper.component as java.awt.Container
                // 工具栏与编辑器是兄弟组件；公共父组件必须显式提供同一个编辑器快照。
                assertEquals(2, pane.componentCount)
                assertTrue(pane is UiDataProvider)
                val values = mutableMapOf<String, Any?>()
                val sink = proxy<DataSink> { name, args ->
                    if (name == "set") values[(args!![0] as DataKey<*>).name] = args[1]
                    null
                }
                (pane as UiDataProvider).uiDataSnapshot(sink)
                val context = DataContext { values[it] }
                assertSame(project, CommonDataKeys.PROJECT.getData(context))
                assertSame(file, CommonDataKeys.VIRTUAL_FILE.getData(context))
                assertSame(wrapper, PlatformCoreDataKeys.FILE_EDITOR.getData(context))
                assertSame(editor, CommonDataKeys.EDITOR.getData(context))
                assertSame(wrapper.state, editorState(context))
                val action = SurroundSubmissionRegionAction()
                val event = AnActionEvent.createFromAnAction(action, null, "test", context)
                action.update(event)
                assertFalse(event.presentation.isEnabled)
                selected = true
                action.update(event)
                assertTrue(event.presentation.isEnabled)
            } finally {
                Disposer.dispose(wrapper)
                if (registered) manager.unregisterAction(Actions.CHALLENGE_EDITOR_TOOLBAR_GROUP)
            }
        }
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.get(0)
                "toString" -> "Test${T::class.java.simpleName}"
                else -> call(method.name, args)
            }
        } as T
}
