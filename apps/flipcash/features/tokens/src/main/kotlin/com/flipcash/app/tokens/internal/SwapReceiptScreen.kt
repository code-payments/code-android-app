package com.flipcash.app.tokens.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.ui.ReceiptLineItem
import com.flipcash.app.core.ui.TokenBalanceRow
import com.flipcash.app.core.ui.TokenBalanceStyle
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.flipcash.app.core.ui.shimmer
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.plus
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.bolded
import com.getcode.view.LoadingSuccessState
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

/**
 * One of the receipt's two anchor rows — a caption over a token logo and the amount it moves.
 *
 * [token] and [amount] are nullable because both resolve asynchronously: the funding token and the
 * confirmed amount can each be briefly absent on first composition. A null of either renders the
 * shimmer stand-in rather than collapsing the row.
 */
internal data class ReceiptAnchor(
    val title: String,
    val token: Token?,
    val amount: Fiat?,
)

/** A label/amount pair in the receipt's middle section (amount to convert, fees). */
internal data class ReceiptLine(
    val label: String,
    val amount: String,
)

/**
 * The confirmation screen behind both Get/Buy and Convert.
 *
 * The two flows are the same screen: a bordered card holding an anchor row, an optional block of
 * line items and a second anchor row, over a warning and a confirm button. They differ only in
 * which side leads — Get reads "You Get / … / You Pay", Convert reads "You Convert / … / You
 * Receive" — and in their copy, so all of that arrives as data. Each flow's own fee math stays in
 * the adapter that owns it, since that is the one piece the two genuinely disagree on.
 *
 * [lineSpacing] is a parameter rather than a constant because v1's Buy receipt shipped a tighter
 * gap between its fee lines than the v2 screens use, and v1 pixels are not ours to change here.
 */
@Composable
private fun SwapReceiptScreen(
    top: ReceiptAnchor,
    lines: List<ReceiptLine>,
    lineSpacing: Dp,
    bottom: ReceiptAnchor,
    warning: String,
    confirmLabel: String,
    progress: LoadingSuccessState,
    onConfirm: () -> Unit,
) {
    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x5),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.grid.x5),
                    text = warning,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    text = confirmLabel,
                    buttonState = ButtonState.Filled,
                    isLoading = progress.loading,
                    isSuccess = progress.success,
                    onClick = onConfirm,
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                CodeTheme.dimens.inset,
                alignment = Alignment.CenterVertically
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = CodeTheme.dimens.border,
                        color = CodeTheme.colors.border,
                        shape = CodeTheme.shapes.medium
                    )
                    .background(White05, CodeTheme.shapes.medium)
                    .padding(
                        horizontal = CodeTheme.dimens.grid.x4,
                        vertical = CodeTheme.dimens.inset
                    ),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Anchor(top)

                if (lines.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(lineSpacing),
                    ) {
                        lines.forEach { line ->
                            ReceiptLineItem(
                                modifier = Modifier.fillMaxWidth(),
                                label = line.label,
                                amount = line.amount,
                            )
                        }
                    }
                }

                Anchor(bottom)
            }
        }
    }
}

@Composable
private fun Anchor(anchor: ReceiptAnchor) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        Text(
            text = anchor.title,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )

        val token = anchor.token
        val amount = anchor.amount
        if (token != null && amount != null) {
            // Token logo, not the currency flag: a receipt names the token being moved, and a flag
            // can't tell two currencies apart when they share one (or have none).
            TokenBalanceRow(
                tokenWithBalance = TokenWithBalance(token = token, balance = amount),
                showName = false,
                showLogo = true,
                showFlag = false,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                styling = rememberTokenBalanceRowStyling(
                    balanceDisplayStyle = TokenBalanceStyle.Large(
                        textStyle = CodeTheme.typography.displaySmall.bolded()
                    ),
                ),
                contentPadding = PaddingValues(0.dp),
            )
        } else {
            AnchorPlaceholder()
        }
    }
}

/**
 * Shimmer stand-in for an anchor row while its token or amount is still resolving. Mirrors the
 * row's layout: a circular logo followed by the balance text.
 */
@Composable
private fun AnchorPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(CodeTheme.dimens.staticGrid.x6)
                .shimmer(CircleShape)
        )
        Box(
            Modifier
                .width(CodeTheme.dimens.grid.x20)
                .height(CodeTheme.dimens.grid.x6)
                .shimmer()
        )
    }
}

/**
 * A fee the currency can't render exactly reads as an approximation ("~ $0.00", "~ ¥0") rather than
 * as a precise figure it isn't.
 *
 * The test is whether anything is lost rounding to display precision, which is what the "~" claims.
 * Two nearby properties answer different questions and each gets a case wrong: the fixed 0.01
 * threshold this replaces is USD-shaped and printed a bare "¥0" for any sub-yen fee, while
 * [Fiat.hasDisplayableValue] asks only "would this format as non-zero" and so drops the "~" from a
 * $0.007 fee that still renders as $0.01.
 *
 * Done in quarks because it is exact there: [Fiat] carries six decimal places, so one displayed
 * unit is a whole number of them. Writing the same check as `rounded(n) != this` would round-trip
 * through [Fiat]'s truncating Double constructor and report exact values as approximate — $2.01 is
 * the first USD case, 1.001 the first for a three-decimal currency.
 */
private fun Fiat.formattedFee(): String {
    val quarksPerDisplayedUnit = smallestUnit.quarks
    val isApproximate = quarksPerDisplayedUnit > 0L && quarks % quarksPerDisplayedUnit != 0L
    return formatted(extraPrefix = if (isApproximate) "~ " else null)
}

@Composable
internal fun TokenBuyReceiptScreen(viewModel: SwapViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    TokenBuyReceiptScreen(state, viewModel::dispatchEvent)
}

@Composable
private fun TokenBuyReceiptScreen(
    state: SwapViewModel.State,
    dispatchEvent: (SwapViewModel.Event) -> Unit,
) {
    val purchaseAmount = state.confirmedEnteredAmount
    val feeAmount = state.feeAmount

    // "You Pay" is always the purchase plus the fee, whichever framing the screen is wearing.
    val totalPaid = purchaseAmount?.let {
        if (!feeAmount.hasDisplayableValue) it else it + feeAmount
    }

    // v2 reframes the buy as a conversion between two currencies the user holds, so the receipt
    // reads "You Get / Amount to convert / Conversion fee" — the same wording Convert uses.
    val isGet = state.isGet

    SwapReceiptScreen(
        top = ReceiptAnchor(
            title = stringResource(
                if (isGet) R.string.subtitle_youGet else R.string.subtitle_youReceive
            ),
            token = state.tokenWithBalance?.token,
            amount = purchaseAmount,
        ),
        lines = if (feeAmount.isPositive && purchaseAmount != null) {
            listOf(
                ReceiptLine(
                    label = stringResource(
                        if (isGet) R.string.label_amountToConvert else R.string.label_amountToBuy
                    ),
                    amount = purchaseAmount.formatted(),
                ),
                ReceiptLine(
                    label = stringResource(
                        if (isGet) R.string.label_conversionFee else R.string.label_exchangeFee
                    ),
                    amount = feeAmount.formattedFee(),
                ),
            )
        } else {
            emptyList()
        },
        // v1's fee lines sit a notch closer together. Gated, not unified: the Buy receipt still
        // renders for v1 and for v2's Add Money, and neither is this change's to restyle.
        lineSpacing = if (isGet) CodeTheme.dimens.grid.x3 else CodeTheme.dimens.grid.x2,
        bottom = ReceiptAnchor(
            title = stringResource(R.string.subtitle_youPay),
            token = state.fundingTokenWithBalance?.token,
            amount = totalPaid,
        ),
        warning = stringResource(R.string.label_buyWarning),
        confirmLabel = stringResource(
            if (isGet) R.string.action_confirm else R.string.action_buy
        ),
        progress = state.buyProgress,
        onConfirm = { dispatchEvent(SwapViewModel.Event.OnBuyConfirmed) },
    )
}

@Composable
internal fun TokenConvertReceiptScreen(viewModel: SwapViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    TokenConvertReceiptScreen(state, viewModel::dispatchEvent)
}

@Composable
private fun TokenConvertReceiptScreen(
    state: SwapViewModel.State,
    dispatchEvent: (SwapViewModel.Event) -> Unit,
) {
    val feeAmount = state.feeAmount

    // Converting out of Dollars charges the fee on top of the entered amount, so the debit is
    // entered + fee and the user receives the full entered amount. Every other direction takes the
    // fee out of the sale, so the debit is what was entered and the receipt is net of the fee.
    val totalDebited = if (state.isConvertingFromDollars) {
        state.enteredAmount + feeAmount
    } else {
        state.enteredAmount
    }

    SwapReceiptScreen(
        top = ReceiptAnchor(
            title = stringResource(R.string.subtitle_youConvert),
            token = state.tokenWithBalance?.token,
            amount = totalDebited,
        ),
        lines = listOf(
            ReceiptLine(
                label = stringResource(R.string.label_amountToConvert),
                amount = state.enteredAmount.formatted(),
            ),
            ReceiptLine(
                label = stringResource(R.string.label_conversionFee),
                amount = feeAmount.formattedFee(),
            ),
        ),
        lineSpacing = CodeTheme.dimens.grid.x3,
        bottom = ReceiptAnchor(
            title = stringResource(R.string.subtitle_youReceive),
            token = state.destinationTokenWithBalance?.token,
            amount = state.netTransferAmount,
        ),
        warning = stringResource(R.string.label_sellWarning),
        confirmLabel = stringResource(R.string.action_confirmConversion),
        progress = state.sellProgress,
        onConfirm = { dispatchEvent(SwapViewModel.Event.OnConvertConfirmed) },
    )
}
