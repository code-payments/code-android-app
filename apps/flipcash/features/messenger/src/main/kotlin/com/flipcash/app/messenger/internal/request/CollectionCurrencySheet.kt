package com.flipcash.app.messenger.internal.request

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.tokens.brandedName
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.features.messenger.R
import com.getcode.navigation.scenes.InWindowBottomSheet
import com.getcode.navigation.scenes.rememberInWindowSheetState
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import com.getcode.theme.White20
import com.getcode.ui.theme.CodeRadioButton
import kotlinx.coroutines.launch

/**
 * Frame 6.3, "Select Payment Currency" (node 13996:243): a grabber, the title, then a collection's
 * currencies by icon and name, each with a trailing radio and no balance. Drawn over the pay sheet,
 * which stays up beneath it with its state. The wallet's own currency list is a different screen
 * and keeps its balance rows.
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
            // The sheet has no grabber of its own; 6.3 draws one.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = CodeTheme.dimens.grid.x2)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(White20),
            )
            Text(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(top = CodeTheme.dimens.grid.x4, bottom = CodeTheme.dimens.grid.x2),
                text = stringResource(R.string.title_selectPaymentCurrency),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
            currencies.forEach { row ->
                val token = row.token
                CollectionCurrencyRow(
                    name = token.brandedName(),
                    image = token.imageUrl,
                    selected = token.address == selected,
                    modifier = Modifier.testTag(collectionCurrencyRowTag(token.address)),
                    onClick = {
                        onPick(token.address)
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

/** One 6.3 row: the token's icon and name, then a radio, filled for the paying currency. */
@Composable
private fun CollectionCurrencyRow(
    name: String,
    image: Any?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TokenIconWithName(
            tokenName = name,
            tokenImage = image,
            imageSize = CodeTheme.dimens.staticGrid.x7,
            textStyle = CodeTheme.typography.screenTitle,
            spacing = CodeTheme.dimens.grid.x3,
        )
        // The row takes the tap; the radio only draws the state.
        CodeRadioButton(selected = selected, onClick = null)
    }
}

internal const val COLLECTION_CURRENCY_SHEET_TAG = "collection_currency_sheet"

internal fun collectionCurrencyRowTag(mint: Mint): String = "collection_currency_${mint.base58()}"
