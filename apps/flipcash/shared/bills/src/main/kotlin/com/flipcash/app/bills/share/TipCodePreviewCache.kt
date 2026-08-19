package com.flipcash.app.bills.share

import android.content.Context
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.share.TipCodePreview
import com.flipcash.app.core.share.TipCodePreviewRenderer
import com.flipcash.app.core.share.TipCodePreviewStorage
import com.flipcash.app.core.share.tipCodePreviewSignature
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.uuid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped cache of rendered tip-code share previews, keyed by user id.
 *
 * Previews are rendered eagerly ([prepare]) when a tip card is populated so the Sharesheet has one
 * ready at share time, then read synchronously ([get]) when the share intent is built. Rendering is
 * best-effort: if it hasn't finished or failed, [get] returns null and the caller shares the URL
 * alone.
 *
 * Freshness is content-addressed via [tipCodePreviewSignature]: a card whose content changed yields
 * a new signature, so [prepare] re-renders and the superseded files are deleted. [invalidate] drops
 * a preview outright (e.g. on regenerate/redeem/expiry). [pruneOnStartup] bounds the on-disk
 * footprint at launch.
 */
@Singleton
class TipCodePreviewCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: TipCodePreviewRenderer,
    private val dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.Default)
    private val entries = ConcurrentHashMap<String, TipCodePreview>()
    private val inFlight = ConcurrentHashMap<String, Deferred<TipCodePreview?>>()

    private fun keyFor(userId: ID): String? = userId.uuid?.toString()

    /** Eagerly render + cache the preview for [card] under [userId]. Idempotent per content. */
    fun prepare(userId: ID, card: Scannable.TipCard) {
        val key = keyFor(userId) ?: return
        val signature = tipCodePreviewSignature(card)

        val cached = entries[key]
        if (cached != null && cached.signature == signature && filesExist(cached)) return
        if (inFlight.containsKey(key)) return

        val deferred = scope.async { renderer.render(card) }
        inFlight[key] = deferred
        scope.launch {
            val preview = runCatching { deferred.await() }.getOrNull()
            inFlight.remove(key, deferred)
            if (preview != null) {
                val previous = entries.put(key, preview)
                if (previous != null && previous.signature != preview.signature) {
                    deleteFiles(previous)
                }
            }
        }
    }

    /** Returns the cached preview for [userId], or null if none is ready / its files are gone. */
    fun get(userId: ID): TipCodePreview? {
        val key = keyFor(userId) ?: return null
        return entries[key]?.takeIf { filesExist(it) }
    }

    /** Drops the cached preview for [userId] and deletes its files. */
    fun invalidate(userId: ID) {
        val key = keyFor(userId) ?: return
        inFlight.remove(key)?.cancel()
        entries.remove(key)?.let { deleteFiles(it) }
    }

    /** Bounds the on-disk preview footprint; safe to call once at app start. */
    fun pruneOnStartup() {
        scope.launch(dispatchers.IO) { TipCodePreviewStorage.prune(context) }
    }

    private fun filesExist(preview: TipCodePreview): Boolean =
        TipCodePreviewStorage.heroFile(context, preview.signature).exists() &&
            TipCodePreviewStorage.squareFile(context, preview.signature).exists()

    private fun deleteFiles(preview: TipCodePreview) {
        runCatching {
            TipCodePreviewStorage.heroFile(context, preview.signature).delete()
            TipCodePreviewStorage.squareFile(context, preview.signature).delete()
        }
    }
}
