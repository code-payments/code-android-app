package com.flipcash.app.messenger.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.screens.components.SpeakerGateBar
import com.flipcash.app.messenger.internal.screens.components.SystemMessageRow
import com.flipcash.app.messenger.internal.screens.components.WrongKeyBanner
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.shared.chat.models.SystemUserLink
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the in-chat invite states (nodes 12846:259, 12846:374, 12846:427 host, 12846:480 joiner). Not an assertion test. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [32], qualifiers = "w390dp-h400dp-xxhdpi")
class InviteChatScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(Modifier.width(390.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File(File("build/screenshots").apply { mkdirs() }, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath}")
    }

    @Test
    fun wrongKey_4d() = render("invite_4d.png") {
        WrongKeyBanner(onLeave = {})
        SpeakerGateBar(
            requirement = ChatRuleRequirement.Never,
            hazeState = rememberHazeState(),
            currencyName = null,
            blurEnabled = false,
            textOverride = "You can’t send messages here",
        )
    }

    @Test
    fun joinedWithInvite_5b() = render("invite_5b.png") {
        SystemMessageRow(
            text = "Alex joined with Jeff Yanta’s invite link",
            userLinks = listOf(SystemUserLink(start = 17, end = 27, userId = listOf(1))),
        )
    }

    @Test
    fun youJoinedWithInvite_5d() = render("invite_5d.png") {
        SystemMessageRow(
            text = "You joined with Jeff Yanta’s invite link",
            userLinks = listOf(SystemUserLink(start = 16, end = 26, userId = listOf(1))),
        )
    }

    @Test
    fun joinedWithYourInvite_5c() = render("invite_5c.png") {
        SystemMessageRow(text = "Alex joined with your invite link")
    }
}
