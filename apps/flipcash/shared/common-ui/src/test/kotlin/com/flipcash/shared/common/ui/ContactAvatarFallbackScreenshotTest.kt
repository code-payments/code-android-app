package com.flipcash.shared.common.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.UserProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * What a [UserProfile]-backed avatar falls back to when the person has no picture: initials for
 * anyone we can name, the silhouette only for a profile that isn't a person.
 *
 * Not an assertion test — it writes to `build/screenshots/` so the three states can be compared by
 * eye. Same mechanics as `ChatIdentityScreenshotTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h400dp-xhdpi")
class ContactAvatarFallbackScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersProfileAvatarFallbacks() {
        val named = UserProfile.Empty.copy(displayName = "Grace Hopper", username = "grace_hopper")
        // No display name — named by handle, so the initial comes from the handle with its `@`
        // stripped. This is the case that used to render a silhouette.
        val handleOnly = UserProfile.Empty.copy(username = "sally_streamer")
        // Not a person: the activity feed passes this for its generic rows.
        val anonymous = UserProfile.Empty

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    listOf(named, handleOnly, anonymous).forEach { profile ->
                        ContactAvatar(
                            userProfile = profile,
                            modifier = Modifier.requiredSize(64.dp).clip(CircleShape),
                        )
                    }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("contact_avatar_fallbacks.png")
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

    /** Crop away the untouched (fully transparent) margin so the PNG is just what was composed. */
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
