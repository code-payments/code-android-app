package com.flipcash.app.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.flipcash.app.core.ui.TokenBalanceRow
import com.flipcash.app.core.ui.TokenBalanceRowStyling
import com.flipcash.app.core.ui.TokenBalanceStyle
import com.flipcash.app.core.ui.TokenSelectionStyle
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.compareTo
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
    styling: TokenBalanceRowStyling = rememberTokenBalanceRowStyling(
        balanceDisplayStyle = TokenBalanceStyle.Pill(),
        selectionStyle = TokenSelectionStyle.Chevron,
    ),
    selectedToken: Mint? = null,
    showSelections: Boolean = false,
    includeReserves: Boolean = true,
    pinFooter: Boolean = false,
    enableGreaterThanAmount: (mint: Mint, LocalFiat) -> Boolean = { _, _ -> true },
    emptyState: (@Composable LazyItemScope.() -> Unit)? = null,
    reserves: (@Composable LazyItemScope.(mint: Mint, cashReserves: LocalFiat) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable (isPinned: Boolean) -> Unit)? = null,
    onTokenSelected: (Token) -> Unit = { },
) {
    val listState = rememberLazyListState()

    val cashReserves = remember(tokens) {
        tokens?.find { it.token.address == Mint.usdf }?.balance ?: LocalFiat.Zero
    }
    val filteredTokens = remember(tokens, includeReserves) {
        if (includeReserves) tokens
        else tokens?.filterNot { it.token.address == Mint.usdf }
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
            header?.let { content ->
                item(contentType = "header") {
                    content()
                }
            }
            if (tokens != null && tokens.isEmpty() && emptyState != null) {
                item(contentType = "empty_state") {
                    emptyState()
                }
            } else {
                items(
                    items = filteredTokens.orEmpty(),
                    key = { item -> item.token.address.base58() },
                    contentType = { "token_row" }) { item ->
                    val updatedIsEnabled by rememberUpdatedState(enableGreaterThanAmount(item.token.address, item.balance))
                    TokenBalanceRow(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .then(itemModifier()),
                        tokenWithBalance = item,
                        showFlag = showFlags,
                        styling = styling,
                        isSelected = (selectedToken == item.token.address).takeIf { showSelections },
                        isEnabled = updatedIsEnabled,
                    ) { onTokenSelected(item.token) }

                    HorizontalDivider(color = CodeTheme.colors.dividerVariant)
                }

                reserves?.let {
                    if (cashReserves.nativeAmount.valueGreaterThan(
                            Fiat(0.0, cashReserves.rate.currency)
                        )
                    ) {
                        item(contentType = "reserves") {
                            it(Mint.usdf, cashReserves)
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = CodeTheme.dimens.inset),
                                color = CodeTheme.colors.dividerVariant
                            )
                        }
                    }
                }

                footer?.let {
                    item(contentType = "footer") {
                        Box(modifier = Modifier.alpha(if (footerSettled) 1f else 0f)) {
                            it(false)
                        }
                    }
                }
            }
        }

        // Pinned overlay — visible while footer list item is off-screen or partially visible
        if (footer != null && !footerSettled && pinFooter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.3f to CodeTheme.colors.background,
                                1f to CodeTheme.colors.background,
                            )
                        )
                    )
                    .padding(top = CodeTheme.dimens.grid.x6)
            ) {
                footer(true)
            }
        }
    }
}
