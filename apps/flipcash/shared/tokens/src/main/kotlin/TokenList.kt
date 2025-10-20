package com.flipcash.app.tokens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flipcash.app.core.ui.TokenBalanceRow
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient

@Composable
fun TokenList(
    tokens: List<TokenWithLocalizedBalance>?,
    modifier: Modifier = Modifier,
    showFlags: Boolean = false,
    emptyState: (@Composable LazyItemScope.() -> Unit)? = null,
    onTokenSelected: (Token) -> Unit = { },
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier
            .verticalScrollStateGradient(
                scrollState = listState,
                color = CodeTheme.colors.background,
                showAtEnd = true
            ),
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
            itemsIndexed(
                tokens.orEmpty(),
                key = { index, item -> item.token.address.base58() }) { index, item ->
                TokenBalanceRow(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset),
                    tokenWithBalance = item,
                    showFlag = showFlags,
                ) { onTokenSelected(item.token) }

                Divider(color = CodeTheme.colors.dividerVariant)
            }
        }
    }
}