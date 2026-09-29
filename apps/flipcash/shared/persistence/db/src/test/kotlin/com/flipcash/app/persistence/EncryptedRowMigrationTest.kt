package com.flipcash.app.persistence

import com.flipcash.app.persistence.converters.ChatTypeConverters
import com.flipcash.app.persistence.converters.MessageContentSerialized
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [FlipcashDatabase.Migration39To40] finds the encrypted rows to mark by a `LIKE` on the stored
 * JSON, so it depends on how the converter writes an encrypted message.
 */
class EncryptedRowMigrationTest {

    private val converters = ChatTypeConverters()
    private val prefix = FlipcashDatabase.Migration39To40.MARK_ENCRYPTED_FOR_OPENING
        .substringAfter("LIKE '").substringBefore("%'")

    @Test
    fun `an encrypted message matches the migration's pattern`() {
        val json = converters.toMessageContentList(
            listOf(MessageContentSerialized.Encrypted(scheme = 1, nonce = "AA", ciphertext = "BB"))
        )!!

        assertTrue(json.startsWith(prefix), json)
    }

    @Test
    fun `a text message doesn't`() {
        val json = converters.toMessageContentList(listOf(MessageContentSerialized.Text("encrypted")))!!

        assertFalse(json.startsWith(prefix), json)
    }
}
