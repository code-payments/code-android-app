package com.getcode.libs.emojis.reactions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One emoji as `emoji_catalog.json` (bundled verbatim from the orchestrator repo) lists it. */
@Serializable
data class EmojiCatalogEntry(
    val emoji: String,
    val name: String,
    val category: String,
    val version: String,
    val skinTone: Boolean,
    val keywords: List<String> = emptyList(),
)

/** `emoji_catalog.json`'s categories, in the file's own order, and the entries in each. */
data class EmojiCatalog(
    val categories: List<String>,
    val entries: List<EmojiCatalogEntry>,
) {
    val byCategory: Map<String, List<EmojiCatalogEntry>> by lazy {
        entries.groupBy { it.category }
    }

    /**
     * Each base emoji that has skin-tone variants, mapped to those variants in catalog order.
     *
     * The catalog lists variants inline (2,030 of its 3,944 entries) and says only that an entry is
     * one, not which emoji it varies. Most are the base with tone modifiers added, so stripping the
     * modifiers (and the U+FE0F presentation selector some bases carry and their variants drop)
     * finds the base. The rest are two-person sequences whose base is a different code point, like
     * 🫱🏻‍🫲🏼 for 🤝, and those name their base before the colon ("handshake: light skin tone, …").
     * The name is only the fallback: "person: medium skin tone, beard" would otherwise group under
     * 🧑 rather than 🧔.
     */
    val skinToneVariants: Map<String, List<EmojiCatalogEntry>> by lazy {
        val bases = entries.filterNot { it.skinTone }
        val byCodePoints = bases.associateBy { it.emoji.withoutToneOrPresentation() }
        val byName = bases.associateBy { it.name }
        val variants = LinkedHashMap<String, MutableList<EmojiCatalogEntry>>()
        for (entry in entries) {
            if (!entry.skinTone) continue
            val base = byCodePoints[entry.emoji.withoutToneOrPresentation()]
                ?: byName[entry.name.substringBefore(':')]
                ?: continue
            variants.getOrPut(base.emoji) { mutableListOf() } += entry
        }
        variants
    }

    /** The first category's entries, the app-layer strip's fill source (decision 1). */
    val firstCategoryEntries: List<EmojiCatalogEntry>
        get() = categories.firstOrNull()?.let { byCategory[it] }.orEmpty()
}

private fun String.withoutToneOrPresentation(): String = buildString {
    var i = 0
    while (i < this@withoutToneOrPresentation.length) {
        val codePoint = this@withoutToneOrPresentation.codePointAt(i)
        if (codePoint !in SKIN_TONE_MODIFIERS && codePoint != VARIATION_SELECTOR_16) appendCodePoint(codePoint)
        i += Character.charCount(codePoint)
    }
}

private val SKIN_TONE_MODIFIERS = 0x1F3FB..0x1F3FF
private const val VARIATION_SELECTOR_16 = 0xFE0F

@Serializable
private data class EmojiCatalogFile(
    val categories: List<String>,
    val emoji: List<EmojiCatalogEntry>,
)

/**
 * Loads [EmojiCatalog] from the bundled `emoji_catalog.json` resource once, on
 * [dispatcher] (default [Dispatchers.Default], since parsing is CPU-bound, not I/O), and caches
 * the result for the loader's lifetime.
 */
class EmojiCatalogLoader(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val resourceLoader: () -> String = { defaultResource() },
) {
    private val mutex = Mutex()
    private var cached: EmojiCatalog? = null

    suspend fun load(): EmojiCatalog {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }
            withContext(dispatcher) {
                val text = resourceLoader()
                val file = json.decodeFromString(EmojiCatalogFile.serializer(), text)
                EmojiCatalog(categories = file.categories, entries = file.emoji).also { cached = it }
            }
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun defaultResource(): String {
            val stream = EmojiCatalogLoader::class.java.classLoader
                ?.getResourceAsStream("emoji_catalog.json")
                ?: error("emoji_catalog.json resource not found")
            return stream.bufferedReader().use { it.readText() }
        }
    }
}
