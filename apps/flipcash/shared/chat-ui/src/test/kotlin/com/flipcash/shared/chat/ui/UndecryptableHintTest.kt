package com.flipcash.shared.chat.ui

import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.chat.UndecryptableReason
import com.flipcash.services.models.chat.MessageContent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UndecryptableHintTest {

    private fun hint(encryption: MessageEncryption?, isFromSelf: Boolean = false, senderName: String = "Ada Lovelace") =
        undecryptableHint(encryption, isFromSelf, senderName)

    @Test
    fun `an unknown scheme or type asks for an update`() {
        assertEquals(
            UndecryptableHint.UpdateApp,
            hint(MessageEncryption.Undecryptable(UndecryptableReason.Unsupported)),
        )
    }

    @Test
    fun `ciphertext with no recorded outcome asks for an update`() {
        assertEquals(UndecryptableHint.UpdateApp, hint(null))
    }

    @Test
    fun `a failed authentication from the peer asks them, by first name, to resend`() {
        assertEquals(
            UndecryptableHint.AskToResend("Ada"),
            hint(MessageEncryption.Undecryptable(UndecryptableReason.Authentication)),
        )
    }

    @Test
    fun `a failed authentication on the viewer's own message asks them to send again`() {
        assertEquals(
            UndecryptableHint.TrySendingAgain,
            hint(MessageEncryption.Undecryptable(UndecryptableReason.Authentication), isFromSelf = true),
        )
    }

    @Test
    fun `an opened or pending message has no hint`() {
        val sealed = MessageContent.Encrypted(scheme = 1, nonce = ByteArray(24), ciphertext = ByteArray(1))

        assertNull(hint(MessageEncryption.Decrypted(sealed)))
        assertNull(hint(MessageEncryption.KeyPending))
    }
}
