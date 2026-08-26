package com.flipcash.app.myaccount.internal.blocklist

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.app.core.blocklist.BlockedUserProfile
import com.flipcash.app.theme.FlipcashPreview
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Instant

/**
 * Renders the blocklist in each identity state to a PNG, so the name-or-handle rule can be
 * eyeballed without an emulator. Not an assertion test — it writes to `build/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class BlocklistScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersRowIdentityStates() {
        val at = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val users = listOf(
            BlockedUserProfile(
                userId = listOf(1.toByte()),
                displayName = "Grace Hopper",
                handle = "@grace_hopper",
                profilePicture = null,
                blockedAt = at,
            ),
            // Blocking is reachable from a tip DM, so a blocked account need never have had a name.
            BlockedUserProfile(
                userId = listOf(2.toByte()),
                displayName = "",
                handle = "@sally_streamer",
                profilePicture = null,
                blockedAt = at,
            ),
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val items = flowOf(PagingData.from(users)).collectAsLazyPagingItems()
            FlipcashPreview(showBackground = true) {
                Box(modifier = Modifier.width(360.dp).height(200.dp)) {
                    BlocklistScreenContent(
                        blocked = items,
                        unblocking = emptyMap(),
                        onUnblock = {},
                    )
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("blocklist_row_identity.png")
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
