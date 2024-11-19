package xyz.flipchat.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.CodeKeyPad
import com.getcode.utils.network.LocalNetworkObserver
import xyz.flipchat.app.R

@Composable
fun AmountWithKeypad(
    modifier: Modifier = Modifier,
    amountAnimatedModel: AmountAnimatedInputUiModel,
    prefix: String = "",
    isKin: Boolean = false,
    hint: String = "",
    onNumberPressed: (Int) -> Unit,
    onBackspace: () -> Unit,
) {
    val networkObserver = LocalNetworkObserver.current
    val networkState by networkObserver.state.collectAsState()

    Column(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.weight(0.65f)
        ) {
            AmountArea(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = CodeTheme.dimens.inset),
                amountPrefix = prefix,
                amountText = "",
                placeholder = "",
                captionText = hint,
                currencyResId = if (isKin) R.drawable.ic_currency_kin else null,
                isAltCaptionKinIcon = false,
                uiModel = amountAnimatedModel,
                isAnimated = true,
                isClickable = false,
                networkState = networkState,
                textStyle = CodeTheme.typography.displayLarge,
            )
        }

        CodeKeyPad(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CodeTheme.dimens.inset)
                .weight(1f),
            onNumber = onNumberPressed,
            onClear = onBackspace,
        )
    }
}