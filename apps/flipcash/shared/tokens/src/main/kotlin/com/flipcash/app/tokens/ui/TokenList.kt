package com.flipcash.app.tokens.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.flipcash.app.core.ui.TokenBalanceRow
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.utils.sheetResignmentBehavior

@Composable
fun TokenList(
    tokens: List<TokenWithLocalizedBalance>?,
    modifier: Modifier = Modifier,
    showFlags: Boolean = false,
    selectedToken: Mint? = null,
    showSelections: Boolean = false,
    emptyState: (@Composable LazyItemScope.() -> Unit)? = null,
    reserves: (@Composable LazyItemScope.(mint: Mint, cashReserves: LocalFiat) -> Unit)? = null,
    footer: (@Composable LazyItemScope.() -> Unit)? = null,
    reservesEnabled: Boolean = false,
    onTokenSelected: (Token) -> Unit = { },
) {
    val listState = rememberLazyListState()

    val cashReserves by remember(tokens) {
        derivedStateOf {
            tokens?.find { it.token.address == Mint.usdf }?.balance ?: LocalFiat.Zero
        }
    }
    val filteredTokens by remember(tokens, reservesEnabled) {
        derivedStateOf {
            if (!reservesEnabled) return@derivedStateOf tokens
            tokens?.filter { it.token.address != Mint.usdf }
        }
    }

    LazyColumn(
        modifier = modifier
            .verticalScrollStateGradient(
                scrollState = listState,
                color = CodeTheme.colors.background,
                showAtEnd = true
            )
            .sheetResignmentBehavior(listState),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        state = listState
    ) {
        if (tokens != null && tokens.isEmpty() && emptyState != null) {
            item {
                emptyState()
            }
        } else {
            items(
                items = filteredTokens.orEmpty(),
                key = { item -> item.token.address.base58() }) { item ->
                TokenBalanceRow(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset),
                    tokenWithBalance = item,
                    showFlag = showFlags,
                    showLogo = !item.isReserves,
                    isSelected = (selectedToken == item.token.address).takeIf { showSelections },
                ) { onTokenSelected(item.token) }

                Divider(color = CodeTheme.colors.dividerVariant)
            }

            reserves?.let {
                if (reservesEnabled &&
                    cashReserves.nativeAmount.valueGreaterThan(
                        Fiat(0.0, cashReserves.rate.currency)
                    )
                ) {
                    item {
                        it(Mint.usdf, cashReserves)
                        Divider(
                            modifier = Modifier.padding(bottom = CodeTheme.dimens.inset),
                            color = CodeTheme.colors.dividerVariant
                        )
                    }
                }
            }

            footer?.let {
                item {
                    it()
                }
            }
        }
    }
}