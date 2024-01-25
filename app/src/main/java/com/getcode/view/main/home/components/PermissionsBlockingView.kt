package com.getcode.view.main.home.components

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.PermissionCheck
import com.getcode.view.components.PermissionsLauncher

@Composable
internal fun PermissionsBlockingView(
    modifier: Modifier = Modifier,
    context: Context,
    onPermissionResult: (Boolean) -> Unit,
    launcher: PermissionsLauncher,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            modifier = Modifier.padding(bottom = 30.dp),
            style = CodeTheme.typography.body1.copy(textAlign = TextAlign.Center),
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
            text = stringResource(id = R.string.action_allowCameraAccess),
            isMaxWidth = false,
            isPaddedVertical = false,
            shape = RoundedCornerShape(45.dp),
            buttonState = ButtonState.Filled
        )
    }
}