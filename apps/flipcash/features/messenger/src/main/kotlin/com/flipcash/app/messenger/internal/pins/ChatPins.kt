package com.flipcash.app.messenger.internal.pins

import com.flipcash.app.messenger.internal.request.DeckPlacement
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.Pin
import com.flipcash.services.models.chat.PinSummary
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SenderIdentity

/**
 * The open chat's pins as `GetPinnedMessages` last returned them, newest pin first.
 *
 * The list is read once on open and again whenever the chat's [PinSummary.version] passes the one
 * it was read at; nothing here builds pins from stream events. `PinsChanged` is best-effort, so a
 * version is a signal to read again rather than a change to apply.
 *
 * @property version the summary version [pins] were read at, or null before the first read.
 * @property summaryCount the stored summary's pin count, which includes deleted messages. Only says
 * whether there is anything to fetch; what shows is counted from [visible].
 * @property stored the transcript's stored copies of pinned messages. Deleting or editing a pinned
 * message moves no pin version, so the read's copy goes stale; a stored copy wins over it.
 */
internal data class ChatPins(
    val version: Long? = null,
    val pins: List<Pin> = emptyList(),
    val summaryCount: Int = 0,
    val stored: Map<Long, ChatMessage> = emptyMap(),
) {
    val loaded: Boolean get() = version != null

    /** Every pinned id, tombstones included: what decides whether the menu offers Pin or Unpin. */
    val pinnedIds: Set<Long> = pins.mapTo(mutableSetOf()) { it.messageId }

    /**
     * The pins that show, each with its newest copy: a message deleted after it was pinned stays
     * pinned but is never drawn.
     */
    val visible: List<Pin> = pins
        .map { pin -> stored[pin.messageId]?.let { pin.copy(message = it) } ?: pin }
        .filterNot { it.message.isTombstone }

    /**
     * Whether a stored summary at [summaryVersion] is newer than what is held. A null summary is a
     * feed or list read that left `pin_summary` unset, which says nothing about pins.
     */
    fun isStale(summaryVersion: Long?): Boolean =
        summaryVersion != null && (version == null || summaryVersion > version)

    /** Takes a read made at [readVersion], unless one at a later version already landed. */
    fun read(pins: List<Pin>, readVersion: Long): ChatPins =
        if (version != null && readVersion < version) this else copy(version = readVersion, pins = pins)

    /** What the bar shows when it points at the [index]th visible pin, or null when it hides. */
    fun bar(index: Int): PinBar? {
        if (!loaded) return if (summaryCount > 0) PinBar.Fetching else null
        if (visible.isEmpty()) return null
        val at = index.coerceIn(0, visible.lastIndex)
        return PinBar.Showing(pin = visible[at], position = at + 1, of = visible.size)
    }

    /**
     * The index the bar moves to after a tap on the pin at [index]: the next older pin, wrapping
     * back to the newest after the oldest.
     */
    fun stepAfter(index: Int): Int = if (visible.isEmpty()) 0 else (index + 1) % visible.size
}

/** What the pin bar draws. */
internal sealed interface PinBar {
    /** Pins exist but the list has not come back yet. */
    data object Fetching : PinBar

    /**
     * [pin] is the [position]th newest of the [of] pins that show. Newest-first, so position 1 is
     * the latest pin.
     */
    data class Showing(val pin: Pin, val position: Int, val of: Int) : PinBar
}

/**
 * The pin bar's segmented rule for the [PinBar.Showing.position]th of [PinBar.Showing.of] pins, or
 * null with one pin, which keeps the single full-height rule.
 *
 * Segments stack bottom up: segment 1, the latest pin, is the bottom one, and each tap lights the
 * one above. The rule's window fits [SEGMENT_WINDOW] of them, so past that the track scrolls just far
 * enough to keep the lit one in view (Figma Cycling variant, node 13016:87: pin 2 of 3 lit at the
 * top of the window, the latest below it, pin 3 scrolled out above).
 */
internal fun PinBar.Showing.segments(): PinSegments? {
    if (of < 2) return null
    val lowest = (position - SEGMENT_WINDOW + 1).coerceIn(1, of - SEGMENT_WINDOW + 1)
    return PinSegments(count = of, lit = position, lowest = lowest)
}

/**
 * @property count every segment on the track, one per visible pin.
 * @property lit the segment drawn in the author's colour, counted from the bottom.
 * @property lowest the bottom segment in the window, counted from the bottom; the track sits
 * `lowest - 1` segments down.
 */
internal data class PinSegments(val count: Int, val lit: Int, val lowest: Int) {
    /** The segments in the window, bottom first. */
    val visible: IntRange get() = lowest..minOf(count, lowest + SEGMENT_WINDOW - 1)
}

internal const val SEGMENT_WINDOW = 2

/** Where pins sit in the chat's chrome. */
internal enum class PinSlot {
    /** Nothing pinned that shows. */
    None,

    /** The bar under the top bar (C1, C6b, C7b). */
    Bar,

    /** A pin button in the top bar's trailing slot that opens the list (C6). */
    TopBarButton,
}

/**
 * Where pins go given the request deck's resting place. Payment requests win the space under the
 * top bar: while the deck rests as the strip, pins step aside to the top bar's trailing slot, free
 * because the request isn't the pill. Once the deck rests as the pill, by scroll or by a swipe up on
 * the strip, the pill holds the trailing slot and the bar comes back. They swap; they never stack.
 *
 * @param hasPins whether the bar has something to show, fetching included.
 * @param deckRest where the request deck rests, or null when the chat has no requests drawn.
 * @param collapsing whether the strip is partway into the pill. The pill grows into the trailing
 * slot from the first pixel, so the button leaves then; the bar waits until the pill is at rest.
 */
internal fun pinSlot(hasPins: Boolean, deckRest: DeckPlacement?, collapsing: Boolean = false): PinSlot = when {
    !hasPins -> PinSlot.None
    deckRest == DeckPlacement.Strip && collapsing -> PinSlot.None
    deckRest == DeckPlacement.Strip -> PinSlot.TopBarButton
    else -> PinSlot.Bar
}

internal val ChatMessage.isTombstone: Boolean
    get() = content.any { it is MessageContent.Deleted }

/**
 * Whether the pinned list has nothing left to do after a fresh read. Opened for a message waiting
 * on a slot ([pinAfterUnpin]), it is done once that message is pinned; otherwise once nothing
 * visible is left in it.
 */
internal fun ChatPins.pinnedListDone(pinAfterUnpin: Long?): Boolean =
    if (pinAfterUnpin != null) pinAfterUnpin in pinnedIds else visible.isEmpty()

/** A message whose pin the server refused at the cap; it is pinned once the viewer frees a slot. */
internal data class WaitingPin(val messageId: Long, val quote: String?)

/**
 * One row of the pinned list: the message as the transcript would draw it, who sent it, and who
 * pinned it.
 */
internal data class PinnedRow(
    val pin: Pin,
    val bubble: ChatListItem.ContentBubble,
    val sender: SenderIdentity,
    val pinnedByName: String,
    /** The message as one line, what a screen reader says for the bubble. */
    val spoken: String? = null,
)
