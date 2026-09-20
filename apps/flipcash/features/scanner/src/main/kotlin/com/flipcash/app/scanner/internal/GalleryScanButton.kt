package com.flipcash.app.scanner.internal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import com.flipcash.features.scanner.R
import com.getcode.ui.core.unboundedClickable

/**
 * The Scan tab's gallery entry point.
 *
 * `PickVisualMedia` runs out of process, so tapping this never asks for a permission -- which is
 * what lets the glyph be a plain static icon rather than a permission prompt with an icon on it.
 * The app declares no `READ_MEDIA_IMAGES` and does not want to start.
 */
@Composable
internal fun GalleryScanButton(
    onImagePicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onImagePicked) }

    Box(
        modifier = modifier
            // A 44dp target around a 32dp glyph: the glyph matches the tab bar's icon size so the
            // control reads as the same weight as a tab, and the target is the minimum that is
            // comfortable to hit at the bottom corner. The ripple is unbounded so it is not
            // clipped to that box -- the glyph sits on the camera preview with no surface behind
            // it, and a square ripple there would read as a button that is not drawn.
            .size(44.dp)
            .unboundedClickable {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = stringResource(R.string.content_description_scanFromGallery),
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}
