package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.push.v1.Model as PushModels
import com.google.protobuf.ByteString
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `push.v1.ChatMetadata.muted` is what silences a notification, and it is the only input to that
 * decision — the planner is handed the decoded model, so a mapping that dropped this field would
 * leave every planner test passing and every muted chat still audible. This covers the one step
 * between the wire and that decision.
 */
class PushPayloadMuteMappingTest {

    private fun payload(
        withChatMetadata: Boolean = true,
        muted: Boolean? = null,
    ): PushModels.Payload {
        val builder = PushModels.Payload.newBuilder()
            .setNavigation(
                PushModels.Navigation.newBuilder()
                    .setChatId(
                        Common.ChatId.newBuilder()
                            .setValue(ByteString.copyFrom(ByteArray(32) { 1 }))
                    )
            )
        if (withChatMetadata) builder.setChatMetadata(chatMetadata(muted))
        return builder.build()
    }

    private fun chatMetadata(muted: Boolean?): PushModels.ChatMetadata =
        PushModels.ChatMetadata.newBuilder()
            .setType(ChatModel.ChatType.CONTACT_DM)
            // Left unset when null, so the default rather than an explicit false is what is read.
            .apply { if (muted != null) setMuted(muted) }
            .build()

    @Test
    fun `a muted push decodes as muted`() {
        val decoded = payload(muted = true).asPayload()
        assertTrue(decoded.chatMetadata!!.muted)
    }

    @Test
    fun `an audible push decodes as not muted`() {
        val decoded = payload(muted = false).asPayload()
        assertFalse(decoded.chatMetadata!!.muted)
    }

    /**
     * The field is a bare `bool`, so a sender that has not been taught to set it yet is
     * indistinguishable from one saying "not muted" — and that is the behaviour we want: an
     * unaware server must leave notifications audible, never silence them.
     */
    @Test
    fun `an unset muted field decodes as not muted`() {
        val decoded = payload(muted = null).asPayload()
        assertFalse(decoded.chatMetadata!!.muted)
    }

    @Test
    fun `a push with no chat metadata has none to read`() {
        assertNull(payload(withChatMetadata = false).asPayload().chatMetadata)
    }

    /** Muting must not disturb anything else the payload carries. */
    @Test
    fun `muting changes only the muted flag`() {
        val audible = payload(muted = false).asPayload()
        val silenced = payload(muted = true).asPayload()
        assertEquals(audible.navigation, silenced.navigation)
        assertEquals(audible.category, silenced.category)
        val audibleChat = audible.chatMetadata!!
        val silencedChat = silenced.chatMetadata!!
        assertEquals(audibleChat.chatType, silencedChat.chatType)
        assertEquals(audibleChat.message, silencedChat.message)
    }
}
