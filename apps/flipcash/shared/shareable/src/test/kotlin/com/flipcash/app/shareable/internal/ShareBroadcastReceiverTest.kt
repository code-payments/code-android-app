package com.flipcash.app.shareable.internal

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.service.chooser.ChooserResult
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.shareable.ShareSheetController.Companion.ACTION_CASH_LINK_SHARED
import com.flipcash.app.shareable.ShareSheetController.Companion.EXTRA_COPIED_TO_CLIPBOARD
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Sharesheet reports what the user did through the chosen-component IntentSender, but it speaks
 * two dialects: picking an app sends EXTRA_CHOSEN_COMPONENT, while the sheet's *own* buttons — the
 * Copy chip in the preview header — send only EXTRA_CHOOSER_RESULT, with no component attached.
 * Reading only the first dialect drops the copy on the floor, and a cash link that is never reported
 * as shared is never funded.
 */
@RunWith(RobolectricTestRunner::class)
class ShareBroadcastReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val relayed = mutableListOf<Intent>()

    private val spy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            relayed += intent
        }
    }

    @Before
    fun setUp() {
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(spy, IntentFilter(ACTION_CASH_LINK_SHARED))
    }

    @After
    fun tearDown() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(spy)
    }

    @Test
    fun `system copy button is reported as a clipboard share`() {
        deliver(Intent().putExtra(EXTRA_CHOOSER_RESULT, chooserResult(ChooserResult.CHOOSER_RESULT_COPY)))

        assertEquals(1, relayed.size)
        assertTrue(relayed.single().getBooleanExtra(EXTRA_COPIED_TO_CLIPBOARD, false))
    }

    @Test
    fun `chosen app is still reported by package name`() {
        val component = ComponentName("com.google.android.apps.messaging", ".ShareActivity")
        deliver(Intent().putExtra(Intent.EXTRA_CHOSEN_COMPONENT, component))

        assertEquals(1, relayed.size)
        assertEquals(
            "com.google.android.apps.messaging",
            relayed.single().getStringExtra(Intent.EXTRA_CHOSEN_COMPONENT)
        )
    }

    @Test
    fun `edit button is not reported as a share`() {
        deliver(Intent().putExtra(EXTRA_CHOOSER_RESULT, chooserResult(ChooserResult.CHOOSER_RESULT_EDIT)))

        assertTrue(relayed.isEmpty())
    }

    @Test
    fun `a result carrying neither component nor chooser result is ignored`() {
        deliver(Intent())

        assertTrue(relayed.isEmpty())
    }

    private fun deliver(intent: Intent) {
        ShareBroadcastReceiver().onReceive(context, intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** [ChooserResult]'s constructor is `@hide`, so a test has to reach for it directly. */
    private fun chooserResult(type: Int, component: ComponentName? = null): ChooserResult =
        ChooserResult::class.java
            .getDeclaredConstructor(
                Int::class.javaPrimitiveType,
                ComponentName::class.java,
                Boolean::class.javaPrimitiveType,
            )
            .apply { isAccessible = true }
            .newInstance(type, component, false)

    private companion object {
        const val EXTRA_CHOOSER_RESULT = "android.intent.extra.CHOOSER_RESULT"
    }
}
