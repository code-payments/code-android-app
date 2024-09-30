package com.getcode.view.main.getKin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.LocalBetaFlags
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.KadoWebScreen
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.CodeKeyPad
import com.getcode.ui.components.Row
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import com.getcode.utils.network.LocalNetworkObserver
import com.getcode.view.main.giveKin.AmountArea
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Composable
fun BuyKinScreen(
    viewModel: BuyKinViewModel = hiltViewModel(),
    onRedirected: () -> Unit,
) {
    val composeScope = rememberCoroutineScope()
    val context = LocalContext.current
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.state.collectAsState()
    val networkObserver = LocalNetworkObserver.current
    val networkState by networkObserver.state.collectAsState()
    val betaFlags = LocalBetaFlags.current
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = CodeTheme.dimens.grid.x4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.weight(0.65f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
            ) {
                AmountArea(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.inset),
                    amountPrefix = dataState.amountModel.amountPrefix,
                    amountSuffix = dataState.amountModel.amountSuffix,
                    amountText = dataState.amountModel.amountText,
                    currencyResId = dataState.currencyModel.selectedCurrency?.resId,
                    isAltCaptionKinIcon = false,
                    uiModel = dataState.amountAnimatedModel,
                    isAnimated = true,
                    isClickable = false,
                    networkState = networkState,
                    textStyle = CodeTheme.typography.displayLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        CodeTheme.dimens.grid.x1,
                        Alignment.CenterHorizontally
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.subtitle_poweredBy),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textSecondary,
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_kado),
                        contentDescription = "Kado"
                    )
                }
            }
        }

        CodeKeyPad(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CodeTheme.dimens.inset)
                .weight(1f),
            onNumber = viewModel::onNumber,
            onClear = viewModel::onBackspace,
            onDecimal = viewModel::onDot,
            isDecimal = false, // no decimal allowed for buys
        )

        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            onClick = {
                if (!networkObserver.isConnected) {
                    ErrorUtils.showNetworkError(context)
                    return@CodeButton
                }

                composeScope.launch {
                    viewModel.initiatePurchase()?.let {
                        if (betaFlags.kadoWebViewEnabled) {
                            navigator.push(KadoWebScreen(it))
                        } else {
                            uriHandler.openUri(it)
                            delay(1.seconds)
                            onRedirected()
                        }
                    }
                }
            },
            enabled = dataState.continueEnabled,
            text = stringResource(R.string.action_next),
            buttonState = ButtonState.Filled,
        )
    }
}