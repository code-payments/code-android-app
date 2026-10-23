package com.getcode.util.resources

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Desktop [ResourceHelper] over the Compose resources generated from `ui:resources`' Android
 * `res/`. IDs come from [ComposeResourceIds]; dimensions and raw resources are not mapped.
 */
class ComposeResourceHelper : ResourceHelper {
    override fun getString(resourceId: Int): String =
        runBlocking { getString(ComposeResourceIds.string(resourceId)) }

    override fun getString(resourceId: Int, vararg formatArgs: Any): String =
        runBlocking { getString(ComposeResourceIds.string(resourceId), *formatArgs) }

    override fun getRawResource(resourceId: Int): String = ""

    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any, default: String): String =
        runCatching {
            runBlocking { getPluralString(ComposeResourceIds.plural(id), quantity, *formatArgs) }
        }.getOrDefault(default)

    override fun getDimension(dimenId: Int, default: Float): Float = default

    override fun getDimensionPixelSize(dimenId: Int, default: Int): Int = default

    override fun getIdentifier(name: String, type: ResourceType): Int? = when (type) {
        ResourceType.Drawable -> ComposeResourceIds.drawableId(name)
        ResourceType.String -> ComposeResourceIds.stringId(name)
    }
}
