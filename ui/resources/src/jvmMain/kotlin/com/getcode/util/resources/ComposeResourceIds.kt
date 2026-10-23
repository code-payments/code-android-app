package com.getcode.util.resources

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * Stable `Int` IDs for the generated Compose resources, so desktop can serve the `Int`-keyed
 * [ResourceHelper] API. Each resource type gets its own range, ordered by resource name.
 */
object ComposeResourceIds {
    private const val DRAWABLE_BASE = 0x7f010000
    private const val STRING_BASE = 0x7f020000
    private const val PLURAL_BASE = 0x7f030000

    private val drawables: List<Pair<String, DrawableResource>> by lazy { Res.allDrawableResources.toSortedList() }
    private val strings: List<Pair<String, StringResource>> by lazy { Res.allStringResources.toSortedList() }
    private val plurals: List<Pair<String, PluralStringResource>> by lazy { Res.allPluralStringResources.toSortedList() }

    fun drawableId(name: String): Int? = drawables.indexOfName(name)?.plus(DRAWABLE_BASE)
    fun stringId(name: String): Int? = strings.indexOfName(name)?.plus(STRING_BASE)

    fun drawable(id: Int): DrawableResource = drawables.at(id - DRAWABLE_BASE, id)
    fun string(id: Int): StringResource = strings.at(id - STRING_BASE, id)
    fun plural(id: Int): PluralStringResource = plurals.at(id - PLURAL_BASE, id)

    private fun <T> Map<String, T>.toSortedList() = entries.sortedBy { it.key }.map { it.key to it.value }

    private fun <T> List<Pair<String, T>>.indexOfName(name: String): Int? =
        indexOfFirst { it.first == name }.takeIf { it >= 0 }

    private fun <T> List<Pair<String, T>>.at(index: Int, id: Int): T =
        getOrNull(index)?.second ?: throw IllegalArgumentException("No Compose resource for ID 0x${id.toString(16)}")
}
