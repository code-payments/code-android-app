package com.flipcash.app.bills.share

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.flipcash.app.core.android.ActivityProvider
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.share.TipCodePreview
import com.flipcash.app.core.share.TipCodePreviewRenderer
import com.flipcash.app.core.share.TipCodePreviewStorage
import com.flipcash.app.core.share.tipCodePreviewSignature
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.utils.trace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Renders [TipCodeShareCard] offscreen into shareable bitmaps.
 *
 * Approach: a [ComposeView] is attached (invisibly) to the current Activity's content view, measured
 * at an explicit pixel size with a pinned [Density], then drawn into a software [Canvas]. Attaching
 * to the live content view (rather than a detached view) is required because the tip code graphic is
 * a real Android `View` (`KikCodeContentView` via `AndroidView`) that only lays out and draws once
 * attached to a window — and it inherits the Activity's lifecycle / saved-state owners for free.
 *
 * A `rememberGraphicsLayer()` capture was rejected: it only works while the layer is live on screen,
 * whereas we render eagerly at a fixed, off-screen size (and in two aspect ratios) that never appears
 * on screen.
 */
class ComposeTipCodePreviewRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityProvider: ActivityProvider,
    private val dispatchers: DispatcherProvider,
) : TipCodePreviewRenderer {

    override suspend fun render(card: Scannable.TipCard): TipCodePreview? {
        val activity = activityProvider.current ?: run {
            trace("tip preview: no foreground activity; skipping render")
            return null
        }
        val signature = tipCodePreviewSignature(card)

        // View work must happen on the main thread.
        val hero = withContext(dispatchers.Main) {
            runCatching { drawOffscreen(activity, card, TipCodeShareVariant.Hero) }.getOrNull()
        } ?: return null
        val square = withContext(dispatchers.Main) {
            runCatching { drawOffscreen(activity, card, TipCodeShareVariant.Square) }.getOrNull()
        } ?: run {
            hero.recycle()
            return null
        }

        // Compress + persist off the main thread.
        return withContext(dispatchers.IO) {
            runCatching {
                val heroFile = TipCodePreviewStorage.heroFile(context, signature)
                val squareFile = TipCodePreviewStorage.squareFile(context, signature)
                writePng(hero, heroFile)
                writePng(square, squareFile)
                TipCodePreview(
                    heroUri = TipCodePreviewStorage.uriFor(context, heroFile),
                    squareUri = TipCodePreviewStorage.uriFor(context, squareFile),
                    signature = signature,
                )
            }.getOrNull().also {
                hero.recycle()
                square.recycle()
            }
        }
    }

    private suspend fun drawOffscreen(
        activity: Activity,
        card: Scannable.TipCard,
        variant: TipCodeShareVariant,
    ): Bitmap? {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        val width = variant.widthPx
        val height = variant.heightPx

        val composeView = ComposeView(activity).apply {
            // Dispose the composition as soon as we detach it below.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            // Present but never visible; alpha 0 still draws into our canvas, unlike GONE/INVISIBLE.
            alpha = 0f
            // Never steal focus or touches during the brief window it's attached.
            isClickable = false
            isFocusable = false
            // Neutralise window insets so the card's statusBar padding is zero offscreen.
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, _ -> WindowInsetsCompat.CONSUMED }
            setContent {
                // Pin density + force the app's (dark) scheme so output is device-independent.
                CompositionLocalProvider(LocalDensity provides Density(RENDER_DENSITY)) {
                    FlipcashTheme {
                        TipCodeShareCard(card = card, variant = variant)
                    }
                }
            }
        }

        root.addView(composeView, ViewGroup.LayoutParams(width, height))
        try {
            awaitRendered(composeView, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                // Solid opaque underlay beneath the (opaque) Compose fill.
                drawColor(AndroidColor.BLACK)
                composeView.draw(this)
            }
            return bitmap
        } finally {
            root.removeView(composeView)
        }
    }

    /**
     * Suspends until [view] has been laid out at the exact target size and its (AndroidView) content
     * has had a couple of frames to draw, or a frame budget is exhausted. Frame-driven so both the
     * framework layout pass and Compose recomposition can settle.
     */
    private suspend fun awaitRendered(view: View, width: Int, height: Int) {
        var frames = 0
        while (frames < MAX_FRAMES) {
            awaitFrame()
            frames++
            val laidOut = view.width == width && view.height == height &&
                view.isLaidOut && !view.isLayoutRequested
            if (laidOut) {
                // Let AndroidView children (the KikCode view) draw their content.
                awaitFrame()
                awaitFrame()
                return
            }
        }
    }

    private suspend fun awaitFrame() = suspendCancellableCoroutine { continuation ->
        val callback = Choreographer.FrameCallback { continuation.resume(Unit) }
        Choreographer.getInstance().postFrameCallback(callback)
        continuation.invokeOnCancellation {
            Choreographer.getInstance().removeFrameCallback(callback)
        }
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private companion object {
        // Fixed density so a dp-defined card rasterises identically on every device.
        const val RENDER_DENSITY = 3f
        // ~upper bound of frames to wait for layout before giving up (best-effort preview).
        const val MAX_FRAMES = 20
    }
}
