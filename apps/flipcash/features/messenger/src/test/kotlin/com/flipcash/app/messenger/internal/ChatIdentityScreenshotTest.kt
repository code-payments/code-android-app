package com.flipcash.app.messenger.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.messenger.internal.screens.components.ContactInfoContainer
import com.flipcash.app.messenger.internal.screens.profile.ProfileHeader
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatType
import com.getcode.navigation.core.CodeNavigator
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Instant

/**
 * Renders the DM info card in each identity state to a PNG so the name-or-handle rule can be
 * eyeballed without an emulator. Not an assertion test — it writes to `build/screenshots/`.
 *
 * Same mechanics as `TokenCardWatermarkScreenshotTest`: pause the clock, pump a fixed number of
 * frames, and draw the Android view directly, so a composable that keeps scheduling frames can't
 * hang `captureToImage()`'s implicit `waitForIdle`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1100dp-xhdpi")
class ChatIdentityScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // A saved device contact: name + number, handle line never applies.
    private val knownContact = ChatParticipant.Contact(
        DeviceContact(
            e164 = "+15551234567",
            androidContactId = 1L,
            displayName = "Ada Lovelace",
            photoUri = null,
            displayNumber = "(555) 123-4567",
        )
    )

    // A tip DM counterparty with both: name on top, handle underneath.
    private val namedTipUser = ChatParticipant.TipUser(
        userId = listOf(1.toByte()),
        profile = UserProfile.Empty.copy(
            displayName = "Grace Hopper",
            username = "grace_hopper",
        ),
    )

    // A tip DM counterparty who never set a name — the case this change fixes. Before, every
    // surface below rendered them as an empty string.
    private val handleOnlyTipUser = ChatParticipant.TipUser(
        userId = listOf(2.toByte()),
        profile = UserProfile.Empty.copy(username = "sally_streamer"),
    )

    @Test
    fun rendersInfoCardIdentityStates() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val cardWidth = Modifier.width(300.dp)
                    ContactInfoContainer(participant = knownContact, modifier = cardWidth)
                    ContactInfoContainer(participant = namedTipUser, modifier = cardWidth)
                    ContactInfoContainer(participant = handleOnlyTipUser, modifier = cardWidth)
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("chat_info_card_identity.png")
    }

    @Test
    fun rendersTopBarIdentityStates() {
        val navigator = mockk<CodeNavigator>(relaxed = true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier.width(360.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    listOf(knownContact, namedTipUser, handleOnlyTipUser).forEach { participant ->
                        ChatTopBar(
                            navigator = navigator,
                            state = ChatViewModel.State(
                                participant = participant,
                                chatType = if (participant is ChatParticipant.TipUser) {
                                    ChatType.TIP_DM
                                } else {
                                    ChatType.CONTACT_DM
                                },
                            ),
                            chatActionHandler = {},
                            dispatch = {},
                        )
                    }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("chat_top_bar_identity.png")
    }

    @Test
    fun rendersProfileHeaderIdentityStates() {
        val joinDate = Instant.fromEpochMilliseconds(1_700_000_000_000)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    listOf(knownContact, namedTipUser, handleOnlyTipUser).forEach { participant ->
                        ProfileHeader(
                            participant = participant,
                            joinDate = joinDate,
                            modifier = Modifier.width(300.dp),
                        )
                    }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("chat_profile_header_identity.png")
    }

    private fun capture(name: String) {
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val width = root.width.takeIf { it > 0 } ?: 1080
        val height = root.height.takeIf { it > 0 } ?: 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val cropped = bitmap.trimmedToDrawnArea()

        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, name)
        file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${cropped.width}x${cropped.height})")
    }

    /**
     * The content view is the full device, but the previews wrap their content — crop away the
     * untouched (fully transparent) margin so the PNG is just what was composed.
     */
    private fun Bitmap.trimmedToDrawnArea(): Bitmap {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] ushr 24 == 0) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return this
        return Bitmap.createBitmap(this, left, top, right - left + 1, bottom - top + 1)
    }
}
