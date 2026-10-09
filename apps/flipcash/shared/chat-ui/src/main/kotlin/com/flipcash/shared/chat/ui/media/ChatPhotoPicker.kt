package com.flipcash.shared.chat.ui.media

import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Launches the system photo picker, which presents as a sheet over the chat, for up to
 * [remaining] images. The attach menu's Photos row calls the returned function directly; there
 * is no in-panel step.
 *
 * Uses `PickVisualMedia` when one slot is left, since `PickMultipleVisualMedia` rejects a max
 * below 2. Picked content URIs go to [onPicked] in selection order; a cancelled pick reports
 * nothing.
 */
@Composable
fun rememberChatPhotoPicker(
    remaining: Int,
    onPicked: (List<Uri>) -> Unit,
): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentRemaining by rememberUpdatedState(remaining)
    val imageOnly = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) currentOnPicked(listOf(uri))
    }
    // getPickImagesMaxLimit() only exists where the system picker does (API 33+, or SDK
    // extension R 2+); elsewhere androidx falls back to a picker with no cap of its own.
    val systemMax = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2)
        ) {
            MediaStore.getPickImagesMaxLimit()
        } else {
            Int.MAX_VALUE
        }
    }
    val max = remaining.coerceIn(2, maxOf(2, systemMax))
    val multiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(max),
    ) { uris ->
        if (uris.isNotEmpty()) currentOnPicked(uris.take(currentRemaining))
    }
    return {
        when {
            currentRemaining <= 0 -> Unit
            currentRemaining == 1 -> single.launch(imageOnly)
            else -> multiple.launch(imageOnly)
        }
    }
}
