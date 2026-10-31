package com.flipcash.shared.chat.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/**
 * Decoded copies of photos this process staged, so a sent photo's bubble has its picture on the
 * first frame it is drawn and keeps it while the row moves from the staged file to the stored blob.
 *
 * Staged photos are kept by chip id; [handOff] moves one to its message's pending client id, which
 * the row keeps after the server copy replaces it. Nothing here outlives the process.
 */
object SentPhotoPreviews {
    private const val MAX_ENTRIES = 12
    private const val MAX_EDGE = 1080

    private val cache = LruCache<String, Bitmap>(MAX_ENTRIES)

    /** The preview for a message's pending client id, if this process staged it. */
    fun forMessage(pendingClientIdHex: String): Bitmap? = cache.get(message(pendingClientIdHex))

    internal fun stage(chipId: String, jpeg: ByteArray) {
        decode(jpeg)?.let { cache.put(chip(chipId), it) }
    }

    internal fun handOff(chipId: String, pendingClientIdHex: String) {
        val bitmap = cache.remove(chip(chipId)) ?: return
        cache.put(message(pendingClientIdHex), bitmap)
    }

    internal fun drop(chipId: String) {
        cache.remove(chip(chipId))
    }

    private fun chip(id: String) = "chip:$id"
    private fun message(hex: String) = "msg:$hex"

    private fun decode(jpeg: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.also { it.prepareToDraw() }
    }.getOrNull()
}
