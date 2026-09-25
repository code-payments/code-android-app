package com.getcode.libs.emojis.reactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiPickerModelTest {

    private val grin = EmojiCatalogEntry(
        emoji = "😀",
        name = "grinning face",
        category = "Smileys & People",
        version = "1.0",
        skinTone = false,
        keywords = listOf("happy", "smile"),
    )
    private val heart = EmojiCatalogEntry(
        emoji = "❤️",
        name = "red heart",
        category = "Smileys & People",
        version = "1.0",
        skinTone = false,
        keywords = listOf("love"),
    )
    private val pizza = EmojiCatalogEntry(
        emoji = "🍕",
        name = "pizza",
        category = "Food & Drink",
        version = "1.0",
        skinTone = false,
        keywords = listOf("food", "slice"),
    )
    private val undrawableEntry = EmojiCatalogEntry(
        emoji = "🫠",
        name = "melting face",
        category = "Smileys & People",
        version = "14.0",
        skinTone = false,
        keywords = emptyList(),
    )

    private val catalog = EmojiCatalog(
        categories = listOf("Smileys & People", "Food & Drink"),
        entries = listOf(grin, heart, pizza, undrawableEntry),
    )

    @Test
    fun `empty query returns frequently used row then one section per category, in catalog order`() {
        val sections = EmojiPickerModel.sections(
            catalog = catalog,
            undrawable = emptySet(),
            recents = listOf(heart.emoji, grin.emoji),
            query = "",
        )

        assertEquals(
            listOf(EmojiPickerModel.FREQUENTLY_USED_ID, "Smileys & People", "Food & Drink"),
            sections.map { it.id },
        )
        assertEquals(listOf(heart, grin), sections.first().entries)
        assertEquals(listOf(grin, heart, undrawableEntry), sections[1].entries)
        assertEquals(listOf(pizza), sections[2].entries)
    }

    @Test
    fun `frequently used row is deduped and undrawable entries are dropped from it and every section`() {
        val sections = EmojiPickerModel.sections(
            catalog = catalog,
            undrawable = setOf(undrawableEntry.emoji),
            recents = listOf(heart.emoji, heart.emoji, undrawableEntry.emoji),
            query = "",
        )

        assertEquals(listOf(heart), sections.first { it.id == EmojiPickerModel.FREQUENTLY_USED_ID }.entries)
        assertTrue(sections.none { entry -> entry.entries.any { it.emoji == undrawableEntry.emoji } })
    }

    @Test
    fun `no frequently used row when recents is empty`() {
        val sections = EmojiPickerModel.sections(
            catalog = catalog,
            undrawable = emptySet(),
            recents = emptyList(),
            query = "",
        )

        assertEquals(listOf("Smileys & People", "Food & Drink"), sections.map { it.id })
    }

    @Test
    fun `search matches name or keyword case-insensitively and returns one section, no frequently used row`() {
        val sections = EmojiPickerModel.sections(
            catalog = catalog,
            undrawable = emptySet(),
            recents = listOf(heart.emoji),
            query = "SLICE",
        )

        assertEquals(listOf(EmojiPickerModel.SEARCH_RESULTS_ID), sections.map { it.id })
        assertEquals(listOf(pizza), sections.first().entries)
    }

    @Test
    fun `blank query (whitespace only) behaves as empty`() {
        val sections = EmojiPickerModel.sections(
            catalog = catalog,
            undrawable = emptySet(),
            recents = emptyList(),
            query = "   ",
        )

        assertEquals(listOf("Smileys & People", "Food & Drink"), sections.map { it.id })
    }

    @Test
    fun `search excludes undrawable entries`() {
        val sections = EmojiPickerModel.sections(
            catalog = catalog,
            undrawable = setOf(undrawableEntry.emoji),
            recents = emptyList(),
            query = "face",
        )

        assertEquals(listOf(grin), sections.first().entries)
    }
}
