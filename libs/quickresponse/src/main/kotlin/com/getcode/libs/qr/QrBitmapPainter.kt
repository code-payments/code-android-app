package com.getcode.libs.qr

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun rememberQrBitmapPainter(
    content: String,
    size: Dp = 150.dp,
    padding: Dp = 0.dp
): BitmapPainter {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val paddingPx = with(density) { padding.roundToPx() }

    // Access the activity to store cache
    val activity = LocalContext.current.getActivity()
        ?: throw IllegalStateException("rememberQrBitmapPainter must be called from an Activity")

    // Initialize cache in activity if not present
    val bitmapCache = remember(activity) {
        activity.getOrCreateBitmapCache()
    }

    var bitmap by remember(content) {
        mutableStateOf(bitmapCache[content])
    }

    LaunchedEffect(content, sizePx, paddingPx) {
        if (bitmap != null) return@LaunchedEffect

        launch(Dispatchers.IO) {
            val cachedBitmap = bitmapCache[content]
            if (cachedBitmap != null) {
                bitmap = cachedBitmap
                return@launch
            }

            val newBitmap = generateQr(
                url = content,
                size = sizePx,
                padding = paddingPx,
            )
            bitmapCache[content] = newBitmap
            bitmap = newBitmap
        }
    }

    return remember(bitmap, sizePx) {
        val currentBitmap = bitmap
            ?: createBitmap(sizePx, sizePx).apply { eraseColor(Color.TRANSPARENT) }
        BitmapPainter(currentBitmap.asImageBitmap())
    }
}

// Extension function to manage cache in Activity
private fun ComponentActivity.getOrCreateBitmapCache(): MutableMap<String, Bitmap> {
    val cacheKey = "QR_BITMAP_CACHE"
    var cache = getExtraData(cacheKey) as? MutableMap<String, Bitmap>
    if (cache == null) {
        cache = mutableMapOf()
        putExtraData(cacheKey, cache)
    }
    return cache
}

// Helper functions to store/retrieve cache (since Activity doesn't have direct extras in Compose)
private fun ComponentActivity.putExtraData(key: String, value: Any) {
    // Using a simple in-memory store; could be replaced with ViewModel or other persistence
    val store = getOrCreateExtraDataStore()
    store[key] = value
}

private fun ComponentActivity.getExtraData(key: String): Any? {
    return getOrCreateExtraDataStore()[key]
}

private fun ComponentActivity.getOrCreateExtraDataStore(): MutableMap<String, Any> {
    val storeKey = "EXTRA_DATA_STORE"
    if (!extrasStore.containsKey(storeKey)) {
        extrasStore[storeKey] = mutableMapOf<String, Any>()
    }
    return extrasStore[storeKey] as MutableMap<String, Any>
}

// In-memory store for extras (could be moved to a ViewModel for better lifecycle handling)
private val extrasStore: MutableMap<String, Any> = mutableMapOf()

fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}