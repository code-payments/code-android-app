package com.flipcash.app.transfers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.app.core.transfers.TransferDirection
import com.flipcash.app.core.ui.BrandedGradientIcon
import com.flipcash.shared.transfers.R
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
fun TransferInformationalScreen(
    direction: TransferDirection
) {
    val navigator = LocalCodeNavigator.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = direction.title,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = navigator::pop
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4)
            ) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    text = direction.continueAction
                ) {
                    navigator.push(direction.nextScreen)
                }

                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    buttonState = ButtonState.Subtle,
                    text = direction.learnMoreAction
                ) {
                    try {
                        uriHandler.openUri(
                            when (direction) {
                                TransferDirection.Incoming -> context.getString(R.string.external_url_deposit)
                                TransferDirection.Outgoing -> context.getString(R.string.external_url_withdraw)
                            }
                        )
                    } catch (_: Exception) {
                        BottomBarManager.showError(
                            title = context.getString(R.string.error_title_failedToOpenExternalLink),
                            message = context.getString(R.string.error_description_failedToOpenExternalLink)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                BrandedGradientIcon(
                    painter = when (direction) {
                        TransferDirection.Incoming -> painterResource(R.drawable.ic_transfer_deposit)
                        TransferDirection.Outgoing -> painterResource(R.drawable.ic_transfer_withdraw)
                    }
                )

                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x10),
                    text = direction.description,
                    style = CodeTheme.typography.textMedium,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textMain
                )
            }
        }
    }
}
