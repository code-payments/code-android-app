package com.flipcash.app.core.ui

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
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders [TokenCard] to a PNG so the USDF "$" watermark can be eyeballed without an emulator.
 * Not an assertion test — it captures the composable to `build/screenshots/`.
 *
 * The screen keeps scheduling frames (Coil's async icon, the balance's AnimatedNumberText, the
 * SharedTransitionLayout in the preview wrapper), so `captureToImage()`'s implicit `waitForIdle`
 * would time out. Instead we pause the clock, pump a fixed number of frames to let layout settle,
 * and draw the Android view directly — a path that can't hang.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TokenCardWatermarkScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun stubImageLoader() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            SuccessResult(
                                image = ColorImage(color = 0x330D3B22),
                                request = chain.request,
                                dataSource = DataSource.MEMORY,
                            ) as ImageResult
                        },
                    )
                }
                .build()
        }
    }

    @Test
    fun rendersUsdfWatermark() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // USDF (Dollars) — gold with the "$" watermark (Figma 9120:15335).
                    TokenCard(
                        token = Token.usdf,
                        balanceText = "$30.00",
                        displayName = "Dollars",
                        appreciationText = "+$0.00",
                    )
                    // A non-USDF token for contrast — no watermark.
                    TokenCard(
                        token = Token.usdc,
                        balanceText = "$12.34",
                    )
                }
            }
        }
        // Pump frames so composition/measure/layout/draw run, without waiting for idle.
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val width = root.width.takeIf { it > 0 } ?: 1080
        val height = root.height.takeIf { it > 0 } ?: 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))

        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, "token_card_watermark.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${width}x$height)")
    }
}
