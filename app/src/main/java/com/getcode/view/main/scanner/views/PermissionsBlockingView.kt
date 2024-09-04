package com.getcode.view.main.scanner.views

import android.Manifest
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.PermissionCheck
import com.getcode.ui.components.PermissionsLauncher

@Composable
internal fun PermissionsBlockingView(
    modifier: Modifier = Modifier,
    context: Context,
    onPermissionResult: (Boolean) -> Unit,
    launcher: PermissionsLauncher,
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth(0.85f)) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp),
                style = CodeTheme.typography.textMedium.copy(textAlign = TextAlign.Center),
                text = stringResource(R.string.subtitle_allowCameraAccess)
            )
            CodeButton(
                onClick = {
                    PermissionCheck.requestPermission(
                        context = context,
                        permission = Manifest.permission.CAMERA,
                        shouldRequest = true,
                        onPermissionResult = onPermissionResult,
                        launcher = launcher
                    )
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(),
                text = stringResource(id = R.string.action_allowCameraAccess),
                shape = CircleShape,
                buttonState = ButtonState.Filled
            )
        }
    }

}