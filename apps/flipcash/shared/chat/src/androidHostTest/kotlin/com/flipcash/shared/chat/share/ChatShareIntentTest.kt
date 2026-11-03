package com.flipcash.shared.chat.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The share intake: how an Android `Intent` becomes the request the shared code handles. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatShareIntentTest {

    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Test
    fun `a text share carries its text`() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "hello")
        assertEquals(ChatShareRequest("hello", emptyList()), intent.toChatShareRequest(resolver))
    }

    @Test
    fun `a stream of unknown type is not an image`() {
        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://other.app/mystery"))
        assertNull(intent.toChatShareRequest(resolver))
    }

    @Test
    fun `a view intent is not a share`() {
        val intent = Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, "hi")
        assertNull(intent.toChatShareRequest(resolver))
    }
}
