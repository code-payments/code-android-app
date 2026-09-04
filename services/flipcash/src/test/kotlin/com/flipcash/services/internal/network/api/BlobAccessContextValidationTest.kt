package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.blob.v1.Model
import com.codeinc.flipcash.gen.blob.v1.validate
import com.flipcash.services.internal.network.extensions.asChatId
import com.flipcash.services.internal.network.extensions.asUserId
import com.flipcash.services.models.chat.ChatId
import dev.bmcreations.protovalidate.ValidationResult
import org.junit.Test
import kotlin.test.assertEquals

/**
 * `AccessContext.scope` is a oneof whose arms are each declared `required`, which the generated
 * validator got wrong until protovalidate-kt 0.1.2: it asserted every arm was the selected one, so
 * whichever arm you set, the others failed as `value is required` and no context could be sent.
 * These pin the behaviour the client relies on — [BlobStorageApi.getBlobs] validates the request
 * with the context already attached.
 */
class BlobAccessContextValidationTest {

    private fun userId(byte: Byte = 2) = ByteArray(16) { byte }.toList()

    private fun chatId(byte: Byte = 1) = ChatId(ByteArray(32) { byte })

    @Test
    fun `a profile scope validates`() {
        val context = Model.AccessContext.newBuilder()
            .setProfile(userId().asUserId())
            .build()

        assertEquals(ValidationResult.Valid, context.validate())
    }

    @Test
    fun `a chat scope validates`() {
        val context = Model.AccessContext.newBuilder()
            .setChat(chatId().asChatId())
            .build()

        assertEquals(ValidationResult.Valid, context.validate())
    }

    @Test
    fun `an unset scope does not validate`() {
        val context = Model.AccessContext.newBuilder().build()

        val result = context.validate()
        assertEquals(true, result is ValidationResult.Invalid)
    }
}
