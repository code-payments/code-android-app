package com.flipcash.shared.chat.media

/**
 * The JPEG bytes of photos this process staged, so a sent photo's bubble has its picture on the
 * first frame it is drawn and keeps it while the row moves from the staged file to the stored blob.
 * The UI decodes them; this holds bytes so the cache is the same on every platform.
 *
 * Staged photos are kept by chip id; [handOff] moves one to its message's pending client id, which
 * the row keeps after the server copy replaces it. Nothing here outlives the process.
 */
object SentPhotoPreviews {
    private const val MAX_ENTRIES = 12

    // Access-ordered, so the eldest entry is the least recently used.
    private val cache = object : LinkedHashMap<String, ByteArray>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > MAX_ENTRIES
    }

    /** The preview for a message's pending client id, if this process staged it. */
    fun forMessage(pendingClientIdHex: String): ByteArray? = synchronized(cache) { cache[message(pendingClientIdHex)] }

    internal fun stage(chipId: String, jpeg: ByteArray) {
        synchronized(cache) { cache[chip(chipId)] = jpeg }
    }

    internal fun handOff(chipId: String, pendingClientIdHex: String) {
        synchronized(cache) {
            val bytes = cache.remove(chip(chipId)) ?: return
            cache[message(pendingClientIdHex)] = bytes
        }
    }

    internal fun drop(chipId: String) {
        synchronized(cache) { cache.remove(chip(chipId)) }
    }

    private fun chip(id: String) = "chip:$id"
    private fun message(hex: String) = "msg:$hex"
}
