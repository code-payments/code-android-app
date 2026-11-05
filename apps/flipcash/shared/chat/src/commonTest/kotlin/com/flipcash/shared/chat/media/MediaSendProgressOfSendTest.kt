package com.flipcash.shared.chat.media

import com.flipcash.shared.chat.media.MediaSendProgress.Failed
import com.flipcash.shared.chat.media.MediaSendProgress.Preparing
import com.flipcash.shared.chat.media.MediaSendProgress.Processing
import com.flipcash.shared.chat.media.MediaSendProgress.Sending
import com.flipcash.shared.chat.media.MediaSendProgress.Sent
import com.flipcash.shared.chat.media.MediaSendProgress.Uploading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The cases match iOS's `ChatPhotoSendProgressTests` "A stack's whole send". */
class MediaSendProgressOfSendTest {

    private fun of(vararg members: MediaSendProgress?) = MediaSendProgress.ofSend(members.toList())

    @Test
    fun `an upload and a photo preparing average to a quarter`() {
        assertEquals(Uploading(0.25f), of(Uploading(0.5f), Preparing))
    }

    @Test
    fun `a processed photo counts as uploaded`() {
        assertEquals(Uploading(0.75f), of(Processing, Uploading(0.5f)))
    }

    @Test
    fun `a photo with no progress counts as sent`() {
        assertEquals(Uploading(0.5f), of(null, Preparing))
    }

    @Test
    fun `a failed photo is left out`() {
        assertEquals(Sending, of(Sent, Sending, Failed(retryable = true)))
    }

    @Test
    fun `every photo preparing reads as preparing`() {
        assertEquals(Preparing, of(Preparing, Preparing))
    }

    @Test
    fun `processing outranks sending, and all sent reads as sent`() {
        assertEquals(Processing, of(Processing, Sending))
        assertEquals(Sent, of(Sent, null))
    }

    @Test
    fun `a stack with no progress draws no overlay`() {
        assertNull(of(null, null))
    }
}
