package com.flipcash.shared.chat.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.Size
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LocalWebPreviewImageLoader
import com.getcode.theme.DesignSystem
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * P22a: a picture already in the loader's memory cache is on the card's first frame, with no
 * wait on a fetch. The fetcher serves the picture once, to warm the cache, and hangs after that,
 * so anything drawn can only have come from memory.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w402dp-h600dp-xhdpi")
class WebCardImageFirstFrameTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val imageUrl = "https://img.example.com/p.png"
    private val fetches = AtomicInteger()

    private val factory = object : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher = Fetcher {
            if (fetches.incrementAndGet() > 1) awaitCancellation()
            val bitmap = Bitmap.createBitmap(1200, 628, Bitmap.Config.ARGB_8888).apply {
                eraseColor(AndroidColor.RED)
            }
            ImageFetchResult(bitmap.asImage(), isSampled = false, dataSource = DataSource.NETWORK) as FetchResult
        }
    }

    @Test
    fun `an image already in memory is drawn on the card's first frame`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val loader = ImageLoader.Builder(context)
            .components { add(factory) }
            .diskCache(null)
            .build()
        runBlocking { loader.execute(ImageRequest.Builder(context).data(imageUrl).size(Size.ORIGINAL).build()) }
        assertEquals(1, fetches.get(), "the warm-up should have fetched once")

        val state = WebLinkCardState(
            url = "https://www.example.com/a",
            resolved = LinkCard.Web.State.Resolved("The title", "A description", imageUrl, "example.com"),
            chipHost = null,
            loading = false,
            host = "example.com",
            ask = {},
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            DesignSystem {
                CompositionLocalProvider(LocalWebPreviewImageLoader provides loader) {
                    Box(Modifier.width(300.dp)) { WebLinkCard(state = state) }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        val pixels = composeRule.onNodeWithTag(WEB_LINK_IMAGE_TAG, useUnmergedTree = true)
            .captureToImage().toPixelMap()
        val centre = pixels[pixels.width / 2, pixels.height / 2]
        assertEquals(1f, centre.red, 0.02f, "the picture should be drawn, not the placeholder tint")
        assertEquals(0f, centre.green, 0.02f)
    }
}
