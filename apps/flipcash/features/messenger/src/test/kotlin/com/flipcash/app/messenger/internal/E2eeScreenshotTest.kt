package com.flipcash.app.messenger.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.messenger.internal.screens.E2eeLearnMoreSheet
import com.flipcash.app.messenger.internal.screens.E2eeSheetKind
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.messenger.R
import com.flipcash.services.models.UserProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the end-to-end encryption surfaces to PNGs for comparison with the design: the DM
 * profile with its footer on and both learn-more sheets. Not an assertion test — it
 * writes to `build/screenshots/`, with the same mechanics as `ChatIdentityScreenshotTest`.
 *
 * The profiles are assembled from the screens' own pieces rather than the screens themselves,
 * which take their view models. The scaffold, header, rows and footer are the ones they use.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w402dp-h874dp-xhdpi")
class E2eeScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersDmSheet() = render("e2ee_sheet_dm.png") {
        E2eeLearnMoreSheet(kind = E2eeSheetKind.Dm, onDismiss = {})
    }

    @Test
    fun rendersGroupSheet() = render("e2ee_sheet_group.png") {
        E2eeLearnMoreSheet(kind = E2eeSheetKind.Group, onDismiss = {})
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) { content() }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }
}
