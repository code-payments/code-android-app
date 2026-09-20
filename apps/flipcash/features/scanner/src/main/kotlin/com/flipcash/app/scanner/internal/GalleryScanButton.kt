package com.flipcash.app.scanner.internal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.flipcash.features.scanner.R
import com.getcode.theme.CodeTheme

/**
 * The Scan tab's gallery entry point, seated on the tab bar's row beside the pill.
 *
 * `PickVisualMedia` runs out of process, so tapping this never asks for a permission -- which is
 * what lets the glyph be a plain static icon rather than a permission prompt with an icon on it.
 * The app declares no `READ_MEDIA_IMAGES` and does not want to start.
 *
 * [modifier] arrives from the bar's trailing slot already sized and already carrying the pill's
 * frosted fill, so this draws only the glyph and the click. Nothing here decides how the control
 * looks against the pill, which is the only way the two can be guaranteed to match.
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
            .testTag("scanner_gallery_button")
            .clickable {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            // A step under the bar's own 30dp icon box because this glyph fills its viewport
            // edge to edge, where the tab drawables carry padding inside theirs. Matching the box
            // drew a visibly heavier icon than the tabs beside it; matching the ink reads level.
            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
            // Outline, like the bar's unselected tabs: the filled twin is what a tab wears when it
            // IS the open tab, and this is an action with no selected state to claim. It stays at
            // full opacity where an unselected tab sits at half, which is the distinction that
            // matters -- same weight, brighter, so it reads as available rather than as a tab.
            imageVector = Icons.Outlined.PhotoLibrary,
            contentDescription = stringResource(R.string.content_description_scanFromGallery),
            tint = Color.White,
        )
    }
}
