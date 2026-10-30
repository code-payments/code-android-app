package com.flipcash.app.tokens.ui

import com.flipcash.services.models.Community
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.HoldingsSummary
import com.flipcash.services.models.chat.RosterSummary
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CommunityChatsTest {
    private fun chat(holdings: HoldingsSummary?, members: Long = 14) = ChatMetadata(
        chatId = ChatId(ByteArray(32)),
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(0),
        rosterSummary = RosterSummary(memberCount = members, version = 1),
        holdingsSummary = holdings,
    )

    private val usd = Rate.oneToOne
    private val usdf = Token.usdf

    private fun summary(quarks: Long) = HoldingsSummary(
        mint = Mint.usdf,
        quarks = quarks,
        holderCount = 9,
        asOf = Instant.fromEpochSeconds(0),
    )

    @Test
    fun `holdings are valued in fiat and drop a zero fraction`() {
        assertEquals(
            CommunityChatHoldings(amount = "$10,840", memberCount = 14),
            chat(summary(10_840_000_000)).communityHoldings(usdf, usd, emptyMap()),
        )
    }

    @Test
    fun `holdings drop the fraction when it rounds to zero cents`() {
        assertEquals(
            "$10,840",
            chat(summary(10_840_004_000)).communityHoldings(usdf, usd, emptyMap()).amount,
        )
    }

    @Test
    fun `holdings keep cents when there is a fraction`() {
        assertEquals(
            "$1,250.50",
            chat(summary(1_250_500_000)).communityHoldings(usdf, usd, emptyMap()).amount,
        )
    }

    @Test
    fun `row falls back to the member count when the server sent no holdings`() {
        assertEquals(
            CommunityChatHoldings(amount = null, memberCount = 14),
            chat(null).communityHoldings(usdf, usd, emptyMap()),
        )
    }

    @Test
    fun `row falls back to the member count when the token is unknown`() {
        assertNull(chat(summary(10_000_000)).communityHoldings(null, usd, emptyMap()).amount)
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
