package com.flipcash.app.core.navigation

import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.services.models.chat.ChatId
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The allowlist that decides what a scan is allowed to produce.
 *
 * A tapped link is something the user went and found; a scanned image is something somebody else
 * can put in front of the camera or send as a photo. The two deserve different answers, and this
 * pins which.
 */
class DeeplinkScanPolicyTest {

    @Test
    fun `only payment routes are scannable`() {
        assertTrue(DeeplinkType.CashLink(ENTROPY).isScannable)
        assertTrue(DeeplinkType.TokenInfo(MINT).isScannable)
        assertTrue(DeeplinkType.TipChat(ChatIdentifier.ByChatId(CHAT_ID)).isScannable)
        assertTrue(DeeplinkType.Tipcard(USER_ID).isScannable)
        assertTrue(DeeplinkType.TipcardByUsername(USERNAME).isScannable)
    }

    @Test
    fun `credential routes are never scannable`() {
        assertFalse(DeeplinkType.Login(ENTROPY).isScannable)
        assertFalse(DeeplinkType.EmailVerification(EMAIL, CODE).isScannable)
    }

    /**
     * Not a security judgement -- an invite is harmless. It is that no scan path has ever opened
     * one: `Scanner`'s `Navigatable` branch has no case for it and falls to `emptyList()`. Naming
     * it here keeps that a stated decision rather than a gap, and flipping it means adding the
     * destination too.
     */
    @Test
    fun `a group chat invite is not a scan destination`() {
        assertFalse(DeeplinkType.GroupChatInvite(CHAT_ID).isScannable)
    }

    private companion object {
        const val ENTROPY = "0123456789abcdef"
        const val USERNAME = "someone"
        const val EMAIL = "someone@example.com"
        const val CODE = "123456"

        val USER_ID: List<Byte> = List(16) { it.toByte() }
        val MINT = Mint(List(32) { it.toByte() })
        val CHAT_ID = ChatId(ByteArray(16) { it.toByte() })
    }
}
