package com.flipcash.app.core.scanner

import android.content.Intent
import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `MainActivity` already owns `ACTION_VIEW`: dozens of `autoVerify` App Link filters route a
 * tapped link through the deeplink path, not this one. [sharedImageUri] has to stay out of that
 * path's way, so the last two cases here aren't padding -- they pin that a text share and a view
 * intent both come back null, leaving `VIEW` free for the deeplink handler to claim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SharedImageIntentTest {

    @Test
    fun singleSendYieldsItsUri() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, IMAGE_URI)
        }
        assertEquals(IMAGE_URI, sharedImageUri(intent))
    }

    @Test
    fun sendMultipleYieldsTheFirstUri() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(IMAGE_URI, OTHER_IMAGE_URI))
        }
        assertEquals(IMAGE_URI, sharedImageUri(intent))
    }

    @Test
    fun aNonImageSendYieldsNothing() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, LINK)
        }
        assertNull(sharedImageUri(intent))
    }

    @Test
    fun aViewIntentYieldsNothing() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LINK))
        assertNull(sharedImageUri(intent))
    }

    private companion object {
        val IMAGE_URI: Uri = Uri.parse("content://media/external/images/media/42")
        val OTHER_IMAGE_URI: Uri = Uri.parse("content://media/external/images/media/43")
        const val LINK = "https://send.flipcash.com/c/#/e=abc"
    }
}
