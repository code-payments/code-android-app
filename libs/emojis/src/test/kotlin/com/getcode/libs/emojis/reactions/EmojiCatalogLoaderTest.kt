package com.getcode.libs.emojis.reactions

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EmojiCatalogLoaderTest {

    @Test
    fun `loads the bundled catalog and preserves category order`() = runBlocking {
        val loader = EmojiCatalogLoader()
        val catalog = loader.load()

        assertTrue(catalog.categories.isNotEmpty(), "catalog has no categories")
        assertTrue(catalog.entries.isNotEmpty(), "catalog has no entries")
        assertEquals(
            listOf(
                "Smileys & People",
                "Animals & Nature",
                "Food & Drink",
                "Travel & Places",
                "Activities",
                "Objects",
                "Symbols",
                "Flags",
            ),
            catalog.categories,
        )

        // Every entry's category is one the file declared, in the same order it declared them.
        val declared = catalog.categories.toSet()
        assertTrue(catalog.entries.all { it.category in declared }, "an entry's category is not in the declared list")

        assertEquals(catalog.categories.first(), "Smileys & People")
        assertTrue(catalog.firstCategoryEntries.isNotEmpty(), "first category has no entries")
        assertTrue(catalog.firstCategoryEntries.all { it.category == catalog.categories.first() })
    }

    @Test
    fun `caches after the first load`() = runBlocking {
        val loader = EmojiCatalogLoader()
        val first = loader.load()
        val second = loader.load()
        assertTrue(first === second, "second load did not return the cached instance")
    }
}
