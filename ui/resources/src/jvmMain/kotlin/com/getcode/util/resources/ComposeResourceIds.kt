package com.getcode.util.resources

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * Stable `Int` IDs for the generated Compose resources, so desktop can serve the `Int`-keyed
 * [ResourceHelper] API.
 *
 * `ui:resources`' own resources get fixed ranges ordered by name. Other modules keep their own
 * `Res` class and register each resource they hand out, getting an ID from a separate range.
 */
object ComposeResourceIds {
    private const val DRAWABLE_BASE = 0x7f010000
    private const val STRING_BASE = 0x7f020000
    private const val PLURAL_BASE = 0x7f030000
    private const val REGISTERED_DRAWABLE_BASE = 0x7f040000
    private const val REGISTERED_STRING_BASE = 0x7f050000
    private const val REGISTERED_PLURAL_BASE = 0x7f060000
    private const val RANGE = 0x10000

    private val drawables: List<Pair<String, DrawableResource>> by lazy { Res.allDrawableResources.toSortedList() }
    private val strings: List<Pair<String, StringResource>> by lazy { Res.allStringResources.toSortedList() }
    private val plurals: List<Pair<String, PluralStringResource>> by lazy { Res.allPluralStringResources.toSortedList() }

    private val registeredDrawables = Registry<DrawableResource>(REGISTERED_DRAWABLE_BASE)
    private val registeredStrings = Registry<StringResource>(REGISTERED_STRING_BASE)
    private val registeredPlurals = Registry<PluralStringResource>(REGISTERED_PLURAL_BASE)

    fun drawableId(name: String): Int? = drawables.indexOfName(name)?.plus(DRAWABLE_BASE)
    fun stringId(name: String): Int? = strings.indexOfName(name)?.plus(STRING_BASE)

    fun register(resource: DrawableResource): Int = registeredDrawables.idOf(resource)
    fun register(resource: StringResource): Int = registeredStrings.idOf(resource)
    fun register(resource: PluralStringResource): Int = registeredPlurals.idOf(resource)

    fun drawable(id: Int): DrawableResource =
        if (id.inRange(REGISTERED_DRAWABLE_BASE)) registeredDrawables[id] else drawables.at(id - DRAWABLE_BASE, id)

    fun string(id: Int): StringResource =
        if (id.inRange(REGISTERED_STRING_BASE)) registeredStrings[id] else strings.at(id - STRING_BASE, id)

    fun plural(id: Int): PluralStringResource =
        if (id.inRange(REGISTERED_PLURAL_BASE)) registeredPlurals[id] else plurals.at(id - PLURAL_BASE, id)

    private fun Int.inRange(base: Int) = this >= base && this < base + RANGE

    private class Registry<T : Any>(private val base: Int) {
        private val ids = HashMap<T, Int>()
        private val resources = ArrayList<T>()

        @Synchronized
        fun idOf(resource: T): Int = ids.getOrPut(resource) {
            resources.add(resource)
            base + resources.size - 1
        }

        @Synchronized
        operator fun get(id: Int): T = resources.getOrNull(id - base)
            ?: throw IllegalArgumentException("No Compose resource for ID 0x${id.toString(16)}")
    }

    private fun <T> Map<String, T>.toSortedList() = entries.sortedBy { it.key }.map { it.key to it.value }

    private fun <T> List<Pair<String, T>>.indexOfName(name: String): Int? =
        indexOfFirst { it.first == name }.takeIf { it >= 0 }

    private fun <T> List<Pair<String, T>>.at(index: Int, id: Int): T =
        getOrNull(index)?.second ?: throw IllegalArgumentException("No Compose resource for ID 0x${id.toString(16)}")
}
