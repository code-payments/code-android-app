package com.flipcash.app.messenger.internal.screens.profile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.RestrictedKind
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BanState
import com.flipcash.services.models.chat.RestrictedUser
import com.flipcash.services.models.chat.SilenceState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Renders the Silenced (AC8a) and Removed (AC8b) rows to PNGs under `build/screenshots/`, to set
 * beside the design frames. Not an assertion test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class RestrictedMembersScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun user(id: Int, name: String, silence: SilenceState? = null, ban: BanState? = null) = RestrictedUser(
        userProfile = UserProfile(
            displayName = name, socialAccounts = emptyList(), phoneNumber = null, email = null,
            userId = listOf(id.toByte()),
        ),
        silence = silence,
        ban = ban,
    )

    private fun render(kind: RestrictedKind, users: List<RestrictedUser>, pending: Int?, file: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(modifier = Modifier.width(360.dp)) {
                    users.forEach { u ->
                        RestrictedRow(
                            kind = kind,
                            user = u,
                            name = u.userProfile.displayName,
                            isWorking = u.userProfile.userId?.firstOrNull()?.toInt() == pending,
                            onOpen = {},
                            onAction = {},
                        )
                    }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }
        capture(file)
    }

    @Test
    fun rendersSilencedList() = render(
        RestrictedKind.Silenced,
        listOf(
            user(1, "Grace Hopper", silence = SilenceState.Until(Clock.System.now() + 1.hours)),
            user(2, "Alan Turing", silence = SilenceState.Forever),
        ),
        pending = 2,
        file = "restricted_silenced.png",
    )

    @Test
    fun rendersRemovedList() = render(
        RestrictedKind.Removed,
        listOf(
            user(3, "Ada Lovelace", ban = BanState(Instant.fromEpochMilliseconds(1_700_000_000_000))),
            user(4, "Edsger Dijkstra", silence = SilenceState.Forever, ban = BanState(Instant.fromEpochMilliseconds(1_700_000_000_000))),
        ),
        pending = null,
        file = "restricted_removed.png",
    )

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
