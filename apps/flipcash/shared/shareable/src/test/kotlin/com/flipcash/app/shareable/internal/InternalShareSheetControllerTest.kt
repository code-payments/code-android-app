package com.flipcash.app.shareable.internal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.shareable.ShareResult
import com.flipcash.app.shareable.ShareSheetController.Companion.ACTION_CASH_LINK_SHARED
import com.flipcash.app.shareable.ShareSheetController.Companion.EXTRA_COPIED_TO_CLIPBOARD
import com.flipcash.app.shareable.Shareable
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The controller has to decide whether a cash link actually left the app, because only then does
 * [com.flipcash.app.shareable.ShareResult.ActionTaken] fund the gift card. Two routes report that:
 * the Sharesheet's broadcast, and -- when the Sharesheet says nothing, as it does for its Copy
 * button before API 35 -- the clipboard.
 */
@RunWith(RobolectricTestRunner::class)
class InternalShareSheetControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clipboard: ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private lateinit var controller: InternalShareSheetController
    private val results = mutableListOf<ShareResult>()

    @Before
    fun setUp() {
        controller = InternalShareSheetController(context, clipboard, FakeResourceHelper())
        controller.onShared = { results += it }
    }

    @Test
    fun `copy reported by the Sharesheet surfaces as a clipboard share`() {
        presentCashLink()

        broadcast(Intent(ACTION_CASH_LINK_SHARED).putExtra(EXTRA_COPIED_TO_CLIPBOARD, true))

        assertEquals(listOf<ShareResult>(ShareResult.CopiedToClipboard), results)
    }

    @Test
    fun `app chosen in the Sharesheet surfaces as a share to that app`() {
        presentCashLink()

        broadcast(
            Intent(ACTION_CASH_LINK_SHARED)
                .putExtra(Intent.EXTRA_CHOSEN_COMPONENT, "com.google.android.apps.messaging")
        )

        assertEquals(
            listOf<ShareResult>(ShareResult.SharedToApp("com.google.android.apps.messaging")),
            results
        )
    }

    @Test
    fun `checkForShare waits for a clipboard it cannot read yet`() {
        presentCashLink()

        // An unreadable clipboard is indistinguishable from an empty one: until the window takes
        // focus, Android denies the read and reports no clip at all.
        controller.checkForShare()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(results.isEmpty(), "reported a result before the clipboard could be read")

        clipboard.setPrimaryClip(ClipData.newPlainText("", "$1.00 https://send.flipcash.com/c/#/e=$ENTROPY"))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))

        assertEquals(listOf<ShareResult>(ShareResult.CopiedToClipboard), results)
    }

    @Test
    fun `checkForShare reports not shared once a readable clipboard turns out not to hold the link`() {
        presentCashLink()
        clipboard.setPrimaryClip(ClipData.newPlainText("", "something else entirely"))

        controller.checkForShare()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf<ShareResult>(ShareResult.NotShared), results)
    }

    @Test
    fun `checkForShare gives up on a clipboard that never becomes readable`() {
        presentCashLink()

        controller.checkForShare()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))

        assertEquals(listOf<ShareResult>(ShareResult.NotShared), results)
    }

    @Test
    fun `a second check does not leave two polls running`() {
        // checkForShare runs on every resume now, so a second one can arrive while the first is
        // still waiting on the clipboard. It has to replace that wait rather than race it.
        presentCashLink()

        controller.checkForShare()
        controller.checkForShare()

        clipboard.setPrimaryClip(ClipData.newPlainText("", "look: $ENTROPY"))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))

        assertEquals(listOf<ShareResult>(ShareResult.CopiedToClipboard), results)
    }

    @Test
    fun `a pending poll does not report again after the Sharesheet reports a copy`() {
        presentCashLink()

        controller.checkForShare()
        broadcast(Intent(ACTION_CASH_LINK_SHARED).putExtra(EXTRA_COPIED_TO_CLIPBOARD, true))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))

        assertEquals(listOf<ShareResult>(ShareResult.CopiedToClipboard), results)
    }

    @Test
    fun `checkForShare does nothing when no share is pending`() {
        controller.checkForShare()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))

        assertTrue(results.isEmpty())
    }

    private fun presentCashLink() {
        val mnemonic = mockk<MnemonicPhrase>(relaxed = true)
        every { mnemonic.getBase58EncodedEntropy() } returns ENTROPY
        val giftCard = mockk<GiftCardAccount>(relaxed = true)
        every { giftCard.mnemonic } returns mnemonic

        runBlocking {
            controller.present(Shareable.CashLink(giftCard, LocalFiat.Zero))
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun broadcast(intent: Intent) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        const val ENTROPY = "2muLdneEFgWMtUejU7WVPj"
    }
}
