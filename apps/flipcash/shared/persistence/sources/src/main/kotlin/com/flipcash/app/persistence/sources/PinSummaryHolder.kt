package com.flipcash.app.persistence.sources

import com.flipcash.services.models.chat.PinSummary
import java.util.concurrent.ConcurrentHashMap

/**
 * The pin summary held for each chat, in memory only.
 *
 * A pin summary's `latest` carries a whole message, which `chat_metadata` has no column shape
 * for, and iOS keeps it in memory too. A restart therefore starts with nothing held; the next
 * feed or `GetChat` read refills it.
 *
 * `Metadata.pin_summary` is unset on the list-view RPCs, and that does not mean nothing is
 * pinned, so a list result must never clear what is held. Versions advance by exactly one per
 * pin or unpin and are compared like `RosterSummary.version`: the greater wins.
 */
internal class PinSummaryHolder {

    private val held = ConcurrentHashMap<String, PinSummary>()

    fun get(chatIdHex: String): PinSummary? = held[chatIdHex]

    /**
     * A chat read from the server. A null or lower-version [incoming] keeps the held summary; an
     * equal or higher one replaces it. Returns the summary now held.
     */
    fun merge(chatIdHex: String, incoming: PinSummary?): PinSummary? =
        held.compute(chatIdHex) { _, current ->
            when {
                current == null -> incoming
                incoming != null && incoming.version >= current.version -> incoming
                else -> current
            }
        }

    /** A `PinsChanged` event: applies [summary] only when strictly newer (nothing held counts as 0). */
    fun applyChanged(chatIdHex: String, summary: PinSummary): Boolean {
        var applied = false
        held.compute(chatIdHex) { _, current ->
            if (summary.version > (current?.version ?: 0L)) {
                applied = true
                summary
            } else {
                current
            }
        }
        return applied
    }

    fun clear() = held.clear()
}
