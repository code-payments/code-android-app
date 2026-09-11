package com.flipcash.services.models

import com.codeinc.flipcash.gen.intent.v1.Model as FlipcashIntentModel
import com.flipcash.services.models.chat.ChatId
import com.getcode.utils.toByteString
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * [buildTipDmPaymentMetadata] and [buildDmPaymentMetadata] are the only places that build a
 * `ChatMetadata.TipDmPayment` / `ChatMetadata.ContactDmPayment`. Nothing round-tripped either
 * through the wire before this, so a mistake in which field gets which value — or in which enum
 * constant a [TipOrigin]/[TipAction] maps to — would ship silently. These parse the built bytes
 * back into `AppMetadata` and assert on the decoded fields, not just on the builder calls.
 */
class DmPaymentMetadataTest {

    private val chatId = ChatId(bytes(32) { it })

    @Test
    fun `tip DM payment sets location TIPCARD and action TIP for a tip-card send`() {
        val bytes = buildTipDmPaymentMetadata(
            chatId = chatId,
            origin = TipOrigin.TIPCARD,
            action = TipAction.TIP,
        )

        val decoded = FlipcashIntentModel.AppMetadata.parseFrom(bytes!!)

        assertEquals(
            FlipcashIntentModel.ChatMetadata.TipDmPayment.Location.TIPCARD,
            decoded.chat.tipDmPayment.location,
        )
        assertEquals(
            FlipcashIntentModel.ChatMetadata.TipDmPayment.Action.TIP,
            decoded.chat.tipDmPayment.action,
        )
        assertNotEquals(
            FlipcashIntentModel.ChatMetadata.TipDmPayment.Action.DEFAULT,
            decoded.chat.tipDmPayment.action,
        )
        assertEquals(chatId.bytes.toByteString(), decoded.chat.chatId.value)
    }

    @Test
    fun `tip DM payment sets location CHAT and action SEND for an in-chat send`() {
        val bytes = buildTipDmPaymentMetadata(
            chatId = chatId,
            origin = TipOrigin.CHAT,
            action = TipAction.SEND,
        )

        val decoded = FlipcashIntentModel.AppMetadata.parseFrom(bytes!!)

        assertEquals(
            FlipcashIntentModel.ChatMetadata.TipDmPayment.Location.CHAT,
            decoded.chat.tipDmPayment.location,
        )
        assertEquals(
            FlipcashIntentModel.ChatMetadata.TipDmPayment.Action.SEND,
            decoded.chat.tipDmPayment.action,
        )
        assertNotEquals(
            FlipcashIntentModel.ChatMetadata.TipDmPayment.Action.DEFAULT,
            decoded.chat.tipDmPayment.action,
        )
    }

    @Test
    fun `tip DM payment metadata is null when chatId is missing`() {
        assertNull(buildTipDmPaymentMetadata(chatId = null, origin = TipOrigin.TIPCARD, action = TipAction.TIP))
    }

    @Test
    fun `contact DM payment round-trips the chat id and both phone numbers`() {
        val bytes = buildDmPaymentMetadata(
            chatId = chatId,
            sourcePhone = "+15550001111",
            destinationPhone = "+15550002222",
        )

        val decoded = FlipcashIntentModel.AppMetadata.parseFrom(bytes!!)

        assertEquals(chatId.bytes.toByteString(), decoded.chat.chatId.value)
        assertEquals("+15550001111", decoded.chat.contactDmPayment.source.value)
        assertEquals("+15550002222", decoded.chat.contactDmPayment.destination.value)
        assertEquals(
            FlipcashIntentModel.ChatMetadata.TypeCase.CONTACT_DM_PAYMENT,
            decoded.chat.typeCase,
        )
    }

    @Test
    fun `contact DM payment metadata is null when any required field is missing`() {
        assertNull(buildDmPaymentMetadata(chatId = null, sourcePhone = "+1", destinationPhone = "+2"))
        assertNull(buildDmPaymentMetadata(chatId = chatId, sourcePhone = null, destinationPhone = "+2"))
        assertNull(buildDmPaymentMetadata(chatId = chatId, sourcePhone = "+1", destinationPhone = null))
    }

    private fun bytes(size: Int, value: (Int) -> Int) = ByteArray(size) { value(it).toByte() }
}
