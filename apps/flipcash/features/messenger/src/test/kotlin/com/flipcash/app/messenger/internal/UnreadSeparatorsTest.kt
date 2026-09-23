package com.flipcash.app.messenger.internal

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.UnreadBoundary
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SeparatorConfig
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What the light pipeline stage emits for a fixed page: one item per gap, the divider carrying a date. */
class UnreadSeparatorsTest {

    private val day1 = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val day2 = day1 + 1.days

    private fun row(id: Long, at: Instant) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = MessageContent.Text("m$id"),
        isFromSelf = false,
        timestamp = at,
    )

    private suspend fun items(boundary: UnreadBoundary, vararg newestFirst: ChatListItem.ContentBubble) =
        flowOf(PagingData.from(newestFirst.toList()).withSeparators(boundary, SeparatorConfig.DayOnly))
            .asSnapshot()

    private val r3 = row(3, day2 + 2.minutes)
    private val r2 = row(2, day2)
    private val r1 = row(1, day1)

    @Test
    fun `divider alone inside a day`() = runTest {
        assertEquals(
            listOf(r3, ChatListItem.UnreadDivider(1, date = null), r2, r1),
            items(UnreadBoundary.At(2, 1), r3, r2, r1).dropDates(),
        )
    }

    @Test
    fun `date alone when there is no boundary`() = runTest {
        assertEquals(
            listOf(r3, r2, ChatListItem.DateSeparator(r2.timestamp), r1),
            items(UnreadBoundary.None, r3, r2, r1),
        )
    }

    @Test
    fun `date and divider on one gap are one item`() = runTest {
        assertEquals(
            listOf(r3, r2, ChatListItem.UnreadDivider(2, date = r2.timestamp), r1),
            items(UnreadBoundary.At(1, 2), r3, r2, r1),
        )
    }

    @Test
    fun `divider above the oldest carries that message's date`() = runTest {
        assertEquals(
            listOf(r3, r2, ChatListItem.DateSeparator(r2.timestamp), r1, ChatListItem.UnreadDivider(3, date = r1.timestamp)),
            items(UnreadBoundary.At(0, 3), r3, r2, r1),
        )
    }

    private fun List<ChatListItem>.dropDates() = filterNot { it is ChatListItem.DateSeparator }
}
