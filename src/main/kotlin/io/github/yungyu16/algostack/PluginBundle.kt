package io.github.yungyu16.algostack

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

object PluginBundle {
    private const val BUNDLE = "messages.PluginBundle"
    private val bundle = DynamicBundle(PluginBundle::class.java, BUNDLE)

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): String =
        bundle.getMessage(key, *params)

    @JvmStatic
    fun messageOfBuildKey(key: String): String? = bundle.messageOrNull(key)
}
