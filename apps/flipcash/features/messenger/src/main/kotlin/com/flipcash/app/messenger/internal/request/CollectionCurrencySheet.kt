package com.flipcash.app.messenger.internal.request

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.tokens.brandedName
import com.flipcash.app.core.ui.TokenBalanceRow
import com.flipcash.app.core.ui.TokenBalanceStyle
import com.flipcash.app.core.ui.TokenSelectionStyle
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.flipcash.features.messenger.R
import com.getcode.navigation.scenes.InWindowBottomSheet
import com.getcode.navigation.scenes.rememberInWindowSheetState
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.launch

/**
 * Frame 6.3, "Select Payment Currency" (node 13996:243): a collection's currencies by name, each
 * with a radio and no balance. Drawn over the pay sheet, which stays up beneath it with its state.
 *
 * A pick changes this payment's currency only: [onPick] replans the payment and buys nothing, and
 * neither the wallet's selected token nor the last paid currency moves. The sheet then slides
 * away and calls [onClosed], as it does when dismissed.
 */
@Composable
internal fun CollectionCurrencySheet(
    currencies: List<TokenWithLocalizedBalance>,
    selected: Mint?,
    onPick: (Mint) -> Unit,
    onClosed: () -> Unit,
) {
    val sheetState = rememberInWindowSheetState()
    val scope = rememberCoroutineScope()
    InWindowBottomSheet(state = sheetState, onDismissRequest = onClosed) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .testTag(COLLECTION_CURRENCY_SHEET_TAG),
        ) {
            Text(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(vertical = CodeTheme.dimens.staticGrid.x5),
                text = stringResource(R.string.title_selectPaymentCurrency),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
            val styling = rememberTokenBalanceRowStyling(
                nameTextStyle = CodeTheme.typography.textMedium,
                balanceDisplayStyle = TokenBalanceStyle.Plain(),
                iconSize = CodeTheme.dimens.staticGrid.x8,
                selectionStyle = TokenSelectionStyle.Checkbox,
                contentPadding = PaddingValues(
                    horizontal = CodeTheme.dimens.inset,
                    vertical = CodeTheme.dimens.staticGrid.x2,
                ),
            )
            currencies.forEach { row ->
                TokenBalanceRow(
                    // The reserve reads "Dollars" here, as it does on the pill.
                    tokenWithBalance = row.copy(displayName = row.token.brandedName()),
                    modifier = Modifier.testTag(collectionCurrencyRowTag(row.token.address)),
                    isSelected = row.token.address == selected,
                    // 6.3 draws no balances.
                    formattedBalance = { "" },
                    styling = styling,
                    onClick = {
                        onPick(row.token.address)
                        scope.launch {
                            sheetState.hide()
                            onClosed()
                        }
                    },
                )
            }
        }
    }
}

internal const val COLLECTION_CURRENCY_SHEET_TAG = "collection_currency_sheet"

internal fun collectionCurrencyRowTag(mint: Mint): String = "collection_currency_${mint.base58()}"
