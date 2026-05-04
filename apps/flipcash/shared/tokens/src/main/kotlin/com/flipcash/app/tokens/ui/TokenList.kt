package com.flipcash.app.tokens.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.flipcash.app.core.ui.TokenBalanceRow
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.isScrolledToEnd
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.utils.sheetResignmentBehavior

@Composable
fun TokenList(
    tokens: List<TokenWithLocalizedBalance>?,
    modifier: Modifier = Modifier,
    itemModifier: LazyItemScope.() -> Modifier = { Modifier },
    showFlags: Boolean = false,
    selectedToken: Mint? = null,
    showSelections: Boolean = false,
    includeReserves: Boolean = false,
    pinFooter: Boolean = false,
    emptyState: (@Composable LazyItemScope.() -> Unit)? = null,
    reserves: (@Composable LazyItemScope.(mint: Mint, cashReserves: LocalFiat) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable (isPinned: Boolean) -> Unit)? = null,
    onTokenSelected: (Token) -> Unit = { },
) {
    val listState = rememberLazyListState()

    val cashReserves by remember(tokens) {
        derivedStateOf {
            tokens?.find { it.token.address == Mint.usdf }?.balance ?: LocalFiat.Zero
        }
    }
    val filteredTokens by remember(tokens, includeReserves) {
        derivedStateOf {
            if (includeReserves) tokens
            else tokens?.filterNot { it.token.address == Mint.usdf }
        }
    }

    val footerSettled by remember {
        derivedStateOf { !pinFooter || !listState.canScrollForward || listState.isScrolledToEnd() }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollStateGradient(
                    scrollState = listState,
                    color = CodeTheme.colors.background,
                    isLongGradient = true,
                )
                .sheetResignmentBehavior(listState),
            state = listState
        ) {
            if (tokens != null && tokens.isEmpty() && emptyState != null) {
                item {
                    emptyState()
                }
            } else {
                header?.let { content ->
                    item {
                        content()
                    }
                }
                items(
                    items = filteredTokens.orEmpty(),
                    key = { item -> item.token.address.base58() }) { item ->
                    TokenBalanceRow(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .then(itemModifier()),
                        tokenWithBalance = item,
                        showFlag = showFlags,
                        isSelected = (selectedToken == item.token.address).takeIf { showSelections },
                    ) { onTokenSelected(item.token) }

                    HorizontalDivider(color = CodeTheme.colors.dividerVariant)
                }

                reserves?.let {
                    if (cashReserves.nativeAmount.valueGreaterThan(
                            Fiat(0.0, cashReserves.rate.currency)
                        )
                    ) {
                        item {
                            it(Mint.usdf, cashReserves)
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = CodeTheme.dimens.inset),
                                color = CodeTheme.colors.dividerVariant
                            )
                        }
                    }
                }

                footer?.let {
                    item {
                        Box(modifier = Modifier.alpha(if (footerSettled) 1f else 0f)) {
                            it(false)
                        }
                    }
                }
            }
        }

        // Pinned overlay — visible while footer list item is off-screen or partially visible
        if (footer != null && !footerSettled && pinFooter) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                footer(true)
            }
        }
    }
}