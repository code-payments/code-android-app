package com.flipcash.app.scanner.internal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import com.flipcash.features.scanner.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.CircularIconButton
import dev.chrisbanes.haze.HazeState

/**
 * The Scan tab's gallery entry point, in the scanner's bottom-right corner.
 *
 * `PickVisualMedia` runs out of process, so tapping this never asks for a permission -- which is
 * what lets the glyph be a plain static icon rather than a permission prompt with an icon on it.
 * The app declares no `READ_MEDIA_IMAGES` and does not want to start.
 *
 * It sits on a frosted circle rather than bare on the preview because the backdrop here is whatever
 * the camera happens to see. A white glyph alone has no contrast it can rely on -- point the phone
 * at a bright wall and it disappears. [hazeState] must be a source that does NOT contain this
 * button, or the blur samples itself; [com.flipcash.app.scanner.internal.bills.ScannableContainer]
 * is what arranges that.
 */
@Composable
internal fun GalleryScanButton(
    onImagePicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onImagePicked) }

    CircularIconButton(
        modifier = modifier,
        buttonSize = CodeTheme.dimens.staticGrid.x10,
        imageSize = CodeTheme.dimens.staticGrid.x5,
        hazeState = hazeState,
        testTag = "scanner_gallery_button",
        onClick = {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
    ) { size: Dp ->
        Icon(
            // Outline rather than filled: filled is the weight the nav bar gives a *selected* tab,
            // and this is an action with no selected state to claim. Nothing on this screen is
            // filled, so the outline is also what matches the surface it sits on.
            imageVector = Icons.Outlined.PhotoLibrary,
            contentDescription = stringResource(R.string.content_description_scanFromGallery),
            tint = Color.White,
            modifier = Modifier.size(size),
        )
    }
}
