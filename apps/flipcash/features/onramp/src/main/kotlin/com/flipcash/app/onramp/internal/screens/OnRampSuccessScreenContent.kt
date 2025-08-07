package com.flipcash.app.onramp.internal.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.app.core.ui.BrandedGradientIcon
import com.flipcash.features.onramp.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun OnRampSuccessScreenContent(
    onClose: () -> Unit,
) {
    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4)
            ) {
                Text(
                    text = stringResource(R.string.subtitle_contactSupportOnRamp),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    text = stringResource(android.R.string.ok)
                ) {
                    onClose()
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                BrandedGradientIcon(
                    painter = rememberVectorPainter(Icons.Default.CheckCircleOutline)
                )

                Text(
                    modifier = Modifier.fillMaxWidth(0.6f),
                    text = stringResource(R.string.title_success),
                    style = CodeTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textMain
                )

                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                    text = stringResource(R.string.subtitle_onrampSuccess),
                    style = CodeTheme.typography.textMedium,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textSecondary
                )
            }
        }
    }
}