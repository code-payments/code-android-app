package com.flipcash.app.tokens.ui

import com.flipcash.services.models.Community
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.HoldingsSummary
import com.flipcash.services.models.chat.RosterSummary
import com.getcode.solana.keys.Mint
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CommunityChatsTest {
    private val us = Locale.US
    private val quarksPerToken = 10_000_000_000L

    private fun chat(holdings: HoldingsSummary?, members: Long = 14) = ChatMetadata(
        chatId = ChatId(ByteArray(32)),
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(0),
        rosterSummary = RosterSummary(memberCount = members, version = 1),
        holdingsSummary = holdings,
    )

    @Test
    fun `quarks format as abbreviated token amounts`() {
        assertEquals("0", formatQuarksCompact(0, us))
        assertEquals("950", formatQuarksCompact(950 * quarksPerToken, us))
        assertEquals("1.2K", formatQuarksCompact(1_250 * quarksPerToken, us))
        assertEquals("3.4M", formatQuarksCompact(3_450_000 * quarksPerToken, us))
        assertEquals("500M", formatQuarksCompact(500_000_000 * quarksPerToken, us))
    }

    @Test
    fun `row text uses holdings quarks and roster member count`() {
        val summary = HoldingsSummary(
            mint = Mint.usdf,
            quarks = 1_250 * quarksPerToken,
            holderCount = 9,
            asOf = Instant.fromEpochSeconds(0),
        )
        assertEquals(
            CommunityChatHoldings(amount = "1.2K", memberCount = 14),
            chat(summary).communityHoldings(us),
        )
    }

    @Test
    fun `row has no holdings text when the server sent none`() {
        assertNull(chat(null).communityHoldings(us))
    }

    @Test
    fun `failed call hides the section`() {
        assertEquals(emptyList(), Result.failure<Community>(Throwable("boom")).topCommunityChats())
    }

    @Test
    fun `empty community hides the section`() {
        val community = Community(mint = Mint.usdf, name = "x", leader = null, topHolderChats = emptyList())
        assertEquals(emptyList(), Result.success(community).topCommunityChats())
    }

    @Test
    fun `chats keep the server's ranking`() {
        val a = chat(null, 1)
        val b = chat(null, 2)
        val community = Community(mint = Mint.usdf, name = "x", leader = null, topHolderChats = listOf(a, b))
        assertEquals(listOf(a, b), Result.success(community).topCommunityChats())
    }
}
