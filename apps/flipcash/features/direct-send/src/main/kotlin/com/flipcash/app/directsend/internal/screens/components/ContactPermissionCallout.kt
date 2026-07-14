package com.flipcash.app.directsend.internal.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.core.ui.Callout
import com.flipcash.app.directsend.internal.SendFlowViewModel
import com.flipcash.app.permissions.ContactAccessHandle
import com.flipcash.features.directsend.R
import com.getcode.theme.CodeTheme
import com.getcode.util.permissions.PermissionResult

@Composable
internal fun ContactPermissionCallout(
    state: SendFlowViewModel.State,
    accessHandle: ContactAccessHandle,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AnimatedVisibility(
        modifier = modifier,
        visible = accessHandle.permissionStatus != PermissionResult.Granted &&
                !state.isPickerMode &&
                !state.hasCalloutBeenDismissed,
        // Enter mirrors the reveal below: a soft, gently-overshooting spring
        // that springs in and scales up from slightly small.
        enter = fadeIn(
            animationSpec = spring(stiffness = Spring.StiffnessLow),
        ) + scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        ) + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        ),
        // Dismiss: scale + fade out while the height springs closed, so the
        // list below slides up cleanly with no bounce.
        exit = fadeOut(
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
        ) + scaleOut(
            targetScale = 0.9f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        ) + shrinkVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        ),
    ) {
        Callout(
            title = stringResource(R.string.title_allowFullContactAccess),
            description = stringResource(R.string.subtitle_allowFullContactAccess),
            icon = {
                Icon(
                    painter = painterResource(com.flipcash.core.R.drawable.ic_callout_warning),
                    contentDescription = null,
                    tint = CodeTheme.colors.warning,
                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x10),
                )
            },
            actionLabel = when (accessHandle.permissionStatus) {
                PermissionResult.Denied -> stringResource(R.string.action_settings)
                PermissionResult.Granted -> "" // guarded by above
                PermissionResult.NotRequested -> stringResource(R.string.action_allowAccess)
                PermissionResult.PermanentlyDenied -> stringResource(R.string.action_settings)
            },
            onDismiss = onDismiss,
            onAction = {
                when (accessHandle.permissionStatus) {
                    PermissionResult.NotRequested -> accessHandle.launch()
                    PermissionResult.Granted -> Unit
                    PermissionResult.Denied,
                    PermissionResult.PermanentlyDenied -> context.launchAppSettings()
                }
            },
            shape = CodeTheme.shapes.medium,
        )
    }
}