package com.getcode.libs.emojis.reactions

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkinToneVariantsTest {

    private fun entry(emoji: String, name: String, skinTone: Boolean = false, category: String = "People") =
        EmojiCatalogEntry(emoji = emoji, name = name, category = category, version = "1.0", skinTone = skinTone)

    private val thumbsUp = entry("👍", "thumbs up")
    private val thumbsUpLight = entry("👍🏻", "thumbs up: light skin tone", skinTone = true)
    private val thumbsUpDark = entry("👍🏿", "thumbs up: dark skin tone", skinTone = true)

    // The base carries U+FE0F; its variants replace it with the modifier.
    private val detective = entry("🕵️‍♂️", "man detective")
    private val detectiveMedium = entry("🕵🏽‍♂️", "man detective: medium skin tone", skinTone = true)

    // A different code point from its variants, so only the name finds it.
    private val handshake = entry("🤝", "handshake")
    private val handshakeMixed = entry("🫱🏻‍🫲🏿", "handshake: light skin tone, dark skin tone", skinTone = true)

    // "person" is a base too, but this variant's code points name the bearded person.
    private val person = entry("🧑", "person")
    private val bearded = entry("🧔", "person: beard")
    private val beardedMedium = entry("🧔🏽", "person: medium skin tone, beard", skinTone = true)

    private val grin = entry("😀", "grinning face")

    private val catalog = EmojiCatalog(
        categories = listOf("People"),
        entries = listOf(
            grin, thumbsUp, thumbsUpLight, thumbsUpDark, detective, detectiveMedium,
            handshake, handshakeMixed, person, bearded, beardedMedium,
        ),
    )

    @Test
    fun `variants group under their base by code points, then by name`() {
        assertEquals(
            mapOf(
                thumbsUp.emoji to listOf(thumbsUpLight, thumbsUpDark),
                detective.emoji to listOf(detectiveMedium),
                handshake.emoji to listOf(handshakeMixed),
                bearded.emoji to listOf(beardedMedium),
            ),
            catalog.skinToneVariants,
        )
    }

    @Test
    fun `category and search sections show only base emoji`() {
        val categories = EmojiPickerModel.sections(catalog, undrawable = emptySet(), recents = emptyList(), query = "")
        assertEquals(listOf(grin, thumbsUp, detective, handshake, person, bearded), categories.single().entries)

        val search = EmojiPickerModel.sections(catalog, undrawable = emptySet(), recents = emptyList(), query = "thumbs")
        assertEquals(listOf(thumbsUp), search.single().entries)
    }

    @Test
    fun `a recent tone stays as picked in frequently used`() {
        val sections = EmojiPickerModel.sections(
            catalog = catalog,
            undrawable = emptySet(),
            recents = listOf(thumbsUpDark.emoji),
            query = "",
        )

        assertEquals(listOf(thumbsUpDark), sections.first { it.id == EmojiPickerModel.FREQUENTLY_USED_ID }.entries)
    }

    @Test
    fun `tone options lead with the base, skip undrawable tones, and are keyed by every choice`() {
        val options = EmojiPickerModel.toneOptions(catalog, undrawable = setOf(thumbsUpLight.emoji))

        val expected = listOf(thumbsUp.emoji, thumbsUpDark.emoji)
        assertEquals(expected, options[thumbsUp.emoji])
        assertEquals(expected, options[thumbsUpDark.emoji])
        assertNull(options[thumbsUpLight.emoji])
        assertNull(options[grin.emoji])
    }

    @Test
    fun `an emoji whose every tone is undrawable has no options`() {
        val options = EmojiPickerModel.toneOptions(catalog, undrawable = setOf(detectiveMedium.emoji))

        assertNull(options[detective.emoji])
    }

    @Test
    fun `every variant in the bundled catalog finds a base`() = runTest {
        val bundled = EmojiCatalogLoader().load()

        val variants = bundled.entries.count { it.skinTone }
        assertEquals(variants, bundled.skinToneVariants.values.sumOf { it.size })
        assertTrue(bundled.skinToneVariants.values.all { it.size == 5 || it.size == 25 })
    }
}
