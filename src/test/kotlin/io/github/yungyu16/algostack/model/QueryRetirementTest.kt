package io.github.yungyu16.algostack.model

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.util.jar.JarFile
import javax.xml.parsers.DocumentBuilderFactory

class QueryRetirementTest {
    @Test
    fun detachedGroupsAreAbsentButAccountAndZoomRemainMounted() {
        val document = javaClass.getResourceAsStream("/META-INF/plugin.xml").use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }
        val nodes = document.getElementsByTagName("group")
        val groups = (0 until nodes.length).map { nodes.item(it) as Element }.associateBy { it.getAttribute("id") }
        assertFalse("AlgoStack.Dojos.TitleToolbar" in groups)
        assertFalse("AlgoStack.Sidebar.Webview.PopupMenu" in groups)
        // 未挂载的纯声明壳不得回归：action 声明必须落在实际挂载的 group 内。
        assertFalse("AlgoStack.Group" in groups)
        assertFalse("AlgoStack.LeetCode.Group" in groups)

        /** 提取 group 直接子项的 action id / reference ref，锁定挂载内容与顺序。 */
        fun childIds(id: String): List<String> {
            val group = requireNotNull(groups[id]) { "Missing group: $id" }
            val children = (0 until group.childNodes.length)
                .map { group.childNodes.item(it) }
                .filterIsInstance<Element>()
                .filter { it.tagName == "action" || it.tagName == "reference" }
            return children.map {
                if (it.tagName == "action") it.getAttribute("id") else it.getAttribute("ref")
            }
        }
        assertEquals(
            listOf("AlgoStack.Actions.Login", "AlgoStack.Dojos.TitleToolbar.Account"),
            childIds(Actions.LEETCODE_SITE_ACTIONS_GROUP)
        )
        val account = requireNotNull(groups["AlgoStack.Dojos.TitleToolbar.Account"])
        assertEquals("io.github.yungyu16.algostack.presentation.actions.AccountGroup", account.getAttribute("class"))
        assertEquals("true", account.getAttribute("popup"))
        assertEquals(listOf("AlgoStack.Actions.UserAccountInfo", "AlgoStack.Actions.Logout"), childIds("AlgoStack.Dojos.TitleToolbar.Account"))
        assertEquals(
            listOf(
                "AlgoStack.Actions.Webview.ZoomIn",
                "AlgoStack.Actions.Webview.ZoomOut",
                "AlgoStack.Actions.Webview.ActualZoom"
            ),
            childIds("AlgoStack.Sidebar.Webview.Toolbar")
        )
        assertEquals(
            listOf("AlgoStack.Dojos.Actions.OpenChallengeGroup"),
            childIds(Actions.CHALLENGES_TABLE_POPUP_GROUP)
        )
        assertEquals(
            listOf(
                "AlgoStack.Actions.RunTest",
                "AlgoStack.Actions.SubmitCode",
                "AlgoStack.Actions.SurroundSubmissionRegion",
                "AlgoStack.Actions.ActivateSolutionsView",
                "AlgoStack.Actions.ActivateDescriptionView"
            ),
            childIds(Actions.CHALLENGE_EDITOR_TOOLBAR_GROUP)
        )

        val actions = document.getElementsByTagName("action")
        for (index in 0 until actions.length) {
            val action = actions.item(index) as Element
            Class.forName(action.getAttribute("class"), false, javaClass.classLoader)
        }
    }

    @Test
    fun queryPersistenceAndIsolatedWidgetsAreNotPackaged() {
        JarFile(requireNotNull(System.getProperty("algostack.pluginArtifact"))).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toSet()
            val root = "io/github/yungyu16/algostack/"
            val serializers = listOf(
                "presentation/QueryOrderSerializer",
                "presentation/InterviewPeriodSerializer",
                "domain/PageSizeSerializer",
                "domain/OrderDirectionSerializer",
                "domain/ChallengeDifficultySerializer",
                "domain/ChallengeStatusSerializer"
            )
            val generated = listOf(
                "presentation/QueryOrder", "presentation/TagSelection", "presentation/ProblemCriteria",
                "presentation/CompanyCriteria", "domain/Pagination"
            ).map { it + "\$\$serializer" }
            val retiredTypes = listOf(
                "presentation/ui/TagLabel",
                "utils/ui/BackgroundRoundedPanel",
                "domain/ChallengeDifficulty\$Advanced",
                "domain/ChallengeDifficulty\$Expert",
                "domain/ChallengeDifficulty\$CodeDojoDefined"
            )
            val retiredQuerySerializers = (serializers + generated).flatMap { path ->
                if (path.startsWith("presentation/")) listOf(
                    path,
                    path.replace("presentation/", "presentation/problems/")
                )
                else listOf(path)
            }
            for (retired in retiredQuerySerializers + retiredTypes) {
                assertFalse(retired, "$root$retired.class" in entries)
            }
            assertFalse(entries.any { it.startsWith("${root}leetcode/api/") })
            // 真实站点 DTO 的序列化器不属于 queryCriteria 持久化。
            for (retained in listOf(
                "leetcode/model/LeetCodeTag\$\$serializer", "leetcode/model/LeetCodeCategoryListItem\$\$serializer"
            )) {
                assertTrue(retained, "$root$retained.class" in entries)
            }
        }
    }
}
