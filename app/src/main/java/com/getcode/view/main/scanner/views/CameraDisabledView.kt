package com.getcode.view.main.scanner.views

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun CameraDisabledView(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.background(CodeTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth(0.85f)) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp),
                style = CodeTheme.typography.textMedium.copy(textAlign = TextAlign.Center),
                text = stringResource(R.string.subtitle_startCameraToScan)
            )
            CodeButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = stringResource(id = R.string.action_startCamera),
                contentPadding = PaddingValues(),
                shape = CircleShape,
                buttonState = ButtonState.Filled
            )
        }
    }
}