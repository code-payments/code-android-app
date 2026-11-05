package com.flipcash.shared.chat.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Photos still sending, which `chat_media_groups.json` doesn't cover: its rows are all confirmed. */
class ChatMediaGroupsPendingTest {

    private val start = Instant.fromEpochSeconds(1_000)

    private fun photo(id: Long, at: Instant, confirmed: Boolean = true) = ChatMediaGroups.Candidate(
        messageId = id,
        senderKey = "me",
        isPhoto = true,
        timestamp = at,
        hasCaption = false,
        isReply = false,
        confirmed = confirmed,
    )

    @Test
    fun `photos still sending stack though their ids are not consecutive`() {
        val rows = listOf(
            photo(-1_000, start, confirmed = false),
            photo(-1_001, start + 1.milliseconds, confirmed = false),
        )

        assertEquals(listOf(listOf(-1_000L, -1_001L)), ChatMediaGroups.stacks(rows))
    }

    @Test
    fun `a confirmed photo and one still sending stack`() {
        val rows = listOf(photo(41, start), photo(-1_001, start + 1.milliseconds, confirmed = false))

        assertEquals(listOf(listOf(41L, -1_001L)), ChatMediaGroups.stacks(rows))
    }

    @Test
    fun `a photo still sending keeps the window`() {
        val rows = listOf(photo(41, start), photo(-1_001, start + 3.seconds, confirmed = false))

        assertEquals(emptyList(), ChatMediaGroups.stacks(rows))
    }

    @Test
    fun `confirmed photos still need consecutive ids`() {
        val rows = listOf(photo(41, start), photo(43, start + 1.milliseconds))

        assertEquals(emptyList(), ChatMediaGroups.stacks(rows))
    }
}
