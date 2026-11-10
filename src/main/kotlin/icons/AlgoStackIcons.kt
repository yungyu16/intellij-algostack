package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon


object AlgoStackIcons {
    @JvmField
    val PLUGIN: Icon = IconLoader.getIcon("/icons/pluginIcon.svg", AlgoStackIcons::class.java.classLoader)

    @JvmField
    val LOGIN: Icon =
        IconLoader.getIcon("/icons/login.svg", AlgoStackIcons::class.java.classLoader)

    @JvmField
    val LOGOUT: Icon =
        IconLoader.getIcon("/icons/logout.svg", AlgoStackIcons::class.java.classLoader)

    @JvmField
    val RUN: Icon = IconLoader.getIcon("/icons/run.svg", AlgoStackIcons::class.java.classLoader)

    @JvmField
    val SUBMIT: Icon = IconLoader.getIcon("/icons/submit.svg", AlgoStackIcons::class.java.classLoader)

    @JvmField
    val SOLUTION: Icon = IconLoader.getIcon("/icons/solution.svg", AlgoStackIcons::class.java.classLoader)

    @JvmField
    val DESCRIPTION: Icon = IconLoader.getIcon("/icons/book.svg", AlgoStackIcons::class.java.classLoader)

    @JvmField
    val APP_BRACKETS: Icon = IconLoader.getIcon("/icons/app-brackets.svg", AlgoStackIcons::class.java.classLoader)

    object Dojos {
        @JvmField
        val LEETCODECN_PATH = "/icons/dojos/leetcodecn.svg"
        @JvmField
        val LEETCODECN: Icon = IconLoader.getIcon(LEETCODECN_PATH, AlgoStackIcons::class.java.classLoader)
    }

    object Languages {
        @JvmField
        val JAVA: Icon =
            IconLoader.getIcon("/icons/languages/java.svg", AlgoStackIcons::class.java.classLoader)
        @JvmField
        val GO: Icon =
            IconLoader.getIcon("/icons/languages/go.svg", AlgoStackIcons::class.java.classLoader)
    }
}
