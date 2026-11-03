package com.flipcash.app.messenger.internal.screens.profile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.MemberActions
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.SilenceState
import com.getcode.theme.CodeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Renders S1's swiped Members row and the member sheet's three states (S3 host on a member, S4 host
 * on a restricted member, S5 a member's view) to PNGs under `build/screenshots/`, to set beside the
 * design frames. Not an assertion test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class MemberModerationScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val restricted = SilenceState.Until(now + 6.hours + 12.minutes)

    private val profile = UserProfile(
        displayName = "Grace Hopper", socialAccounts = emptyList(), phoneNumber = null, email = null,
        userId = listOf(7),
    )

    private fun renderRow(actions: MemberActions, silence: SilenceState?, file: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(modifier = Modifier.width(360.dp)) {
                    MemberSwipeRow(
                        actions = actions,
                        stateKey = 7,
                        revealGroup = null,
                        onRestrict = {},
                        onLift = {},
                        onRemove = {},
                        initiallyRevealed = true,
                    ) {
                        MemberRow(
                            member = ChatMember(userId = listOf(7), userProfile = profile, pointers = emptyList(), joinedAt = now - 3.hours),
                            name = profile.displayName,
                            isHost = false,
                            age = JoinAge.of(now - 3.hours, now),
                            restriction = if (actions.showsRestriction) restrictionLabel(silence, now) else null,
                            onOpen = {},
                        )
                    }
                }
            }
        }
        repeat(20) { composeRule.mainClock.advanceTimeByFrame() }
        capture(file)
    }

    private fun renderSheet(actions: MemberActions, silence: SilenceState?, file: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Box(modifier = Modifier.width(400.dp).background(CodeTheme.colors.surface)) {
                    MemberSheetContent(
                        profile = profile,
                        name = profile.displayName,
                        handle = "@grace",
                        groupTitle = "Book Club",
                        actions = actions,
                        silence = silence,
                        now = now,
                        onViewProfile = {},
                        onReport = {},
                        onRestrict = {},
                        onLift = {},
                        onRemove = {},
                        onDismiss = {},
                    )
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }
        capture(file)
    }

    @Test
    fun rendersSwipedRow() = renderRow(MemberActions(restrict = true, remove = true), null, "member_row_swiped.png")

    @Test
    fun rendersSwipedRestrictedRow() = renderRow(
        MemberActions(lift = true, remove = true, showsRestriction = true),
        restricted,
        "member_row_swiped_restricted.png",
    )

    @Test
    fun rendersHostSheet() = renderSheet(MemberActions(restrict = true, remove = true), null, "member_sheet_host.png")

    @Test
    fun rendersHostSheetOnRestrictedMember() = renderSheet(
        MemberActions(lift = true, remove = true, showsRestriction = true),
        restricted,
        "member_sheet_host_restricted.png",
    )

    @Test
    fun rendersMemberSheet() = renderSheet(MemberActions.None, null, "member_sheet_member.png")

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
