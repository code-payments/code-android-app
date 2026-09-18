package com.flipcash.app.shareable.internal

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.shareable.Shareable
import com.flipcash.shared.shareable.R
import com.getcode.util.resources.FakeResourceHelper
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The shared text is what the recipient reads, so it is asserted verbatim — the blank line between
 * the invitation and the link is what keeps the URL unfurling on its own instead of running into
 * the sentence. iOS' GroupInviteSheet sends the same two lines.
 */
@RunWith(RobolectricTestRunner::class)
class ShareGroupInviteTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    private val resources = FakeResourceHelper()
        .stub(R.string.message_groupInvite, "Join %1\$s on Flipcash and let's chat")

    private val controller = InternalShareSheetController(
        context = application,
        clipboardManager = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager,
        resources = resources,
    )

    @Test
    fun `titled group shares the invitation above the link`() = runTest {
        val sent = present(
            Shareable.GroupInvite(url = "https://app.flipcash.com/chat/abc", title = "Book Club")
        )

        assertEquals(
            "Join Book Club on Flipcash and let's chat\n\nhttps://app.flipcash.com/chat/abc",
            sent.getStringExtra(Intent.EXTRA_TEXT)
        )
        assertEquals(
            "Join Book Club on Flipcash and let's chat",
            sent.getStringExtra(Intent.EXTRA_TITLE)
        )
        assertEquals(
            "Join Book Club on Flipcash and let's chat",
            sent.getStringExtra(Intent.EXTRA_SUBJECT)
        )
    }

    /**
     * An untitled group reaches the controller as "" rather than null — `ChatSubject.Group.title`
     * is `groupTitle.orEmpty()` — so blank has to fall back the same way null does, or the
     * invitation goes out with a hole where the name should be.
     */
    @Test
    fun `blank title shares the bare link`() = runTest {
        val sent = present(
            Shareable.GroupInvite(url = "https://app.flipcash.com/chat/abc", title = "   ")
        )

        assertEquals("https://app.flipcash.com/chat/abc", sent.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(sent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun `untitled group shares the bare link`() = runTest {
        val sent = present(
            Shareable.GroupInvite(url = "https://app.flipcash.com/chat/abc", title = null)
        )

        assertEquals("https://app.flipcash.com/chat/abc", sent.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(sent.getStringExtra(Intent.EXTRA_TITLE))
        assertNull(sent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    /** Presents [shareable] and returns the intent the chooser was built around. */
    private suspend fun present(shareable: Shareable): Intent {
        controller.present(shareable)
        val chooser = shadowOf(application).nextStartedActivity
        return requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
    }
}
