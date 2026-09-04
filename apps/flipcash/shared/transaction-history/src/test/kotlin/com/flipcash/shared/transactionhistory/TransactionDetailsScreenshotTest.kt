package com.flipcash.shared.transactionhistory

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import coil3.ColorImage
import coil3.Image
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.compose.ExchangeStub
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every [TransactionKind] of [TransactionDetailsContent] to a PNG in `build/screenshots/`,
 * so the states can be eyeballed against Figma 9708:105260 without an emulator. Not an assertion
 * test.
 *
 * The screen never goes idle (Coil's images, the preview wrapper's SharedTransitionLayout), so
 * `captureToImage()`'s implicit `waitForIdle` would hang. It pauses the clock, pumps a fixed number
 * of frames, and draws the Android view directly — the same path
 * `TokenCardWatermarkScreenshotTest` takes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class TransactionDetailsScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Serve every image from memory, synchronously.
     *
     * The sample mints point at unreachable URLs, and Coil's real pipeline is asynchronous even
     * when it is only going to fail — which is why one render could come out with its token icons
     * missing while an identically-shaped one next to it had them. An interceptor that answers from
     * [DataSource.MEMORY] takes the async path out entirely, so what lands in the PNG no longer
     * depends on how many frames were pumped.
     */
    @Before
    fun stubImageLoader() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            SuccessResult(
                                image = tokenArt(chain.request.data)
                                    ?: ColorImage(color = 0x00000000),
                                request = chain.request,
                                dataSource = DataSource.MEMORY,
                            ) as ImageResult
                        },
                    )
                }
                .build()
        }
    }

    @Test fun youTipped() = render(0)
    @Test fun youReceived() = render(1)
    @Test fun youSent() = render(2)
    @Test fun youGaveCash() = render(3)
    @Test fun youReceivedCash() = render(4)
    @Test fun youSentCashLink() = render(5)
    @Test fun buy() = render(6)
    @Test fun sell() = render(7)
    @Test fun withdraw() = render(8)
    @Test fun deposit() = render(9)
    @Test fun convert() = render(10)
    @Test fun unknown() = render(11)
    @Test fun openCashLink() = render(12)

    private fun render(index: Int) {
        val (name, details) = TransactionDetailsSamples.All[index]

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Harness { TransactionDetailsContent(details = details) }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("txn_details_$name.png")
    }

    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        FlipcashPreview(showBackground = true) {
            CompositionLocalProvider(
                LocalExchange provides ExchangeStub(context = LocalContext.current),
            ) {
                content()
            }
        }
    }

    /**
     * Each mint's own icon, decoded from `src/test/resources/tokens/`. The app draws token art from
     * the mint's [com.getcode.opencode.model.financial.Token.imageUrl], never from a bundled
     * drawable, so the renders resolve the fixtures' URLs to the tokens' real icons rather than
     * standing in an app asset that belongs to a different screen.
     */
    private fun tokenArt(data: Any?): Image? {
        val file = data.toString().substringAfterLast("/tokens/", missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() } ?: return null
        val bytes = javaClass.getResourceAsStream("/tokens/$file")?.use { it.readBytes() } ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImage()
    }

    private fun capture(name: String) {
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val width = root.width.takeIf { it > 0 } ?: 800
        val height = root.height.takeIf { it > 0 } ?: 1720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))

        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }
}
