package com.flipcash.app.messenger.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.flipcash.features.messenger.R
import com.getcode.manager.BottomBarManager
import com.getcode.ui.components.bars.BottomBarView
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.screens.profile.GroupInviteContent
import com.flipcash.app.messenger.internal.screens.profile.InviteLandingSamples
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.shared.chat.invite.DeadInviteReason
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders each invite landing state to `build/screenshots/` so it can be set beside its frame.
 * Not an assertion test. Same mechanics as [GroupChromeScreenshotTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// API 32: the frosted-glass button modifier compiles an AGSL shader on 33+, which Robolectric cannot.
@Config(sdk = [32], qualifiers = "w390dp-h844dp-xxhdpi")
class InviteLandingScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * [then], when given, replaces [state] after the first frames, as the view model does when
     * Join Group is pressed, so a button keeps the size it was laid out with.
     */
    private fun render(
        name: String,
        state: InviteLandingState,
        then: InviteLandingState? = null,
        overlay: @Composable BoxScope.() -> Unit = {},
    ) {
        composeRule.mainClock.autoAdvance = false
        var current by mutableStateOf(state)
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Box(Modifier.width(390.dp).height(844.dp)) {
                    GroupInviteContent(current, {}, {}, {}, {})
                    overlay()
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }
        if (then != null) {
            composeRule.runOnUiThread {
                current = then
                Snapshot.sendApplyNotifications()
            }
            repeat(20) { composeRule.mainClock.advanceTimeByFrame() }
        }
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File(File("build/screenshots").apply { mkdirs() }, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }

    @Test fun invite_3a() = render("invite_3a.png", InviteLandingSamples.preview)
    @Test fun joining_4a() = render("invite_4a.png", InviteLandingSamples.preview, then = InviteLandingSamples.joining)
    @Test fun exhausted_3c() = render("invite_3c_exhausted.png", InviteLandingSamples.dead(DeadInviteReason.EXHAUSTED))
    @Test fun expired_3c() = render("invite_3c_expired.png", InviteLandingSamples.dead(DeadInviteReason.EXPIRED))
    @Test fun revoked_3c() = render("invite_3c_revoked.png", InviteLandingSamples.dead(DeadInviteReason.REVOKED))
    /** 4b: the link died between the preview and the tap; the screen becomes 3c in place, no alert. */
    @Test fun diedBeforeTap_4b() = render("invite_4b.png", InviteLandingSamples.preview, then = InviteLandingSamples.dead(DeadInviteReason.EXHAUSTED))

    /** 4c: a transport failure leaves the preview as it was, under the app-level alert. */
    @Test fun networkFailure_4c() = render("invite_4c.png", InviteLandingSamples.preview) {
        val activity = composeRule.activity
        val message = remember {
            BottomBarManager.clear()
            BottomBarManager.showError(
                title = activity.getString(R.string.error_title_failedToJoinGroup),
                message = activity.getString(R.string.error_description_failedToJoinGroupAlert),
            )
            BottomBarManager.messages.value.last().also { BottomBarManager.clear() }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        Box(Modifier.align(Alignment.BottomCenter)) {
            BottomBarView(bottomBarMessage = message, onShown = {}, onClose = {}, onBackPressed = {})
        }
    }

    @Test fun notFound_3e() = render("invite_3e.png", InviteLandingSamples.notFound)
    @Test fun banned_3f() = render("invite_3f.png", InviteLandingSamples.banned)
}
