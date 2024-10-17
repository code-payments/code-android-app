package com.flipchat.features.balance

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.oct24.R
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import com.getcode.model.chat.Chat
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.White10
import com.getcode.ui.components.chat.ChatNode
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.utils.Kin
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data object BalanceScreen : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel = getActivityScopedViewModel<BalanceSheetViewModel>()
        val state by viewModel.stateFlow.collectAsState()
        BalanceScreenContent(state, viewModel::dispatchEvent)
    }

}
@Composable
fun BalanceScreenContent(
    state: BalanceSheetViewModel.State,
    dispatch: (BalanceSheetViewModel.Event) -> Unit,
) {
    val navigator = LocalCodeNavigator.current

    BalanceContent(
        state = state,
        dispatch = dispatch,
        faqOpen = {  },
        openChat = {  },
        buyMoreKin = {  }
    )
}

@Composable
fun BalanceContent(
    state: BalanceSheetViewModel.State,
    dispatch: (BalanceSheetViewModel.Event) -> Unit,
    faqOpen: () -> Unit,
    openChat: (Chat) -> Unit,
    buyMoreKin: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val navigator = LocalCodeNavigator.current

    val chatsEmpty by remember(state.chats) {
        derivedStateOf { state.chats.isEmpty() }
    }

    val canClickBalance by remember(state.currencySelection.enabled) {
        derivedStateOf { state.currencySelection.enabled }
    }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth(),
        state = lazyListState
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset,)
                    .padding(top = CodeTheme.dimens.grid.x7)
            ) {
                BalanceTop(
                    state,
                    canClickBalance,
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
            ) {
                if (!chatsEmpty && !state.chatsLoading && !state.isKinSelected) {
                    KinValueHint(faqOpen)
                }
            }
        }

        itemsIndexed(
            state.chats,
            key = { _, item -> item.id },
            contentType = { _, item -> item }
        ) { index, chat ->
            ChatNode(chat = chat, onClick = { openChat(chat) })
            Divider(
                modifier = Modifier.padding(start = CodeTheme.dimens.inset),
                color = White10,
            )
        }

        when {
            state.chatsLoading -> {
                item {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            CodeTheme.dimens.grid.x2,
                            CenterVertically
                        ),
                    ) {
                        CodeCircularProgressIndicator()
                        Text(
                            modifier = Modifier.fillMaxWidth(0.6f),
                            text = stringResource(R.string.subtitle_loadingBalanceAndTransactions),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

//            chatsEmpty -> {
//                item {
//                    EmptyTransactionsHint(faqOpen)
//                }
//            }
        }
    }
}

@Composable
fun BalanceTop(
    state: BalanceSheetViewModel.State,
    isClickable: Boolean,
    onClick: () -> Unit = {}
) {
    if (state.amountText.isEmpty()) {
        CodeCircularProgressIndicator()
    } else {
        AmountArea(
            amountText = state.amountText,
            isAltCaption = false,
            isAltCaptionKinIcon = false,
            isLoading = state.chatsLoading,
            currencyResId = state.currencyFlag,
            isClickable = false,
            onClick = onClick,
            textStyle = CodeTheme.typography.displayLarge,
        )
    }
}

@Composable
private fun ColumnScope.KinValueHint(onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .align(CenterHorizontally)
    ) {
        val annotatedBalanceString = buildAnnotatedString {
            val infoString = stringResource(R.string.subtitle_valueKinChanges)
            val actionString = stringResource(R.string.subtitle_learnMore)
            val textString = "$infoString $actionString"

            val startIndex = textString.indexOf(actionString)
            val endIndex = textString.length
            append(textString)

            addStyle(
                style = SpanStyle(
                    textDecoration = TextDecoration.Underline
                ), start = startIndex, end = endIndex
            )
            addStyle(
                style = SpanStyle(color = CodeTheme.colors.textSecondary),
                start = 0,
                end = textString.length
            )
            addStringAnnotation(
                tag = stringResource(R.string.subtitle_learnMore),
                annotation = "",
                start = startIndex,
                end = endIndex
            )
        }

        ClickableText(
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.grid.x1),
            text = annotatedBalanceString,
            style = CodeTheme.typography.textMedium,
            onClick = {
                annotatedBalanceString
                    .getStringAnnotations(
                        context.getString(R.string.subtitle_learnMore),
                        it,
                        it
                    )
                    .firstOrNull()?.let { onClick() }
            }
        )
    }
}

@Composable
private fun EmptyTransactionsHint(faqOpen: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .height(200.dp)
            .padding(horizontal = CodeTheme.dimens.grid.x6),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier
                .align(CenterHorizontally)
        ) {
            Text(
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x1),
                text = stringResource(R.string.subtitle_dontHaveKin),
                color = CodeTheme.colors.textSecondary,
                style = CodeTheme.typography.textMedium
            )
        }

        val annotatedLinkString: AnnotatedString = buildAnnotatedString {
            val linkString = "Check out the FAQ"
            val remainderString = " to find out how to get some."
            val textString = linkString + remainderString

            val startIndex = textString.indexOf(linkString)
            val endIndex = linkString.length

            append(textString)
            addStyle(
                style = SpanStyle(
                    color = CodeTheme.colors.textSecondary,
                ), start = 0, end = textString.length
            )
            addStyle(
                style = SpanStyle(
                    textDecoration = TextDecoration.Underline
                ), start = startIndex, end = endIndex
            )

            addStringAnnotation(
                tag = context.getString(R.string.title_faq),
                annotation = "",
                start = startIndex,
                end = endIndex
            )
        }

        Row(
            modifier = Modifier
                .align(CenterHorizontally)
        ) {
            ClickableText(
                text = annotatedLinkString,
                style = CodeTheme.typography.textMedium.copy(textAlign = TextAlign.Center),
                onClick = {
                    annotatedLinkString
                        .getStringAnnotations(
                            context.getString(R.string.title_faq),
                            it,
                            it
                        )
                        .firstOrNull()?.let { _ -> faqOpen() }
                }
            )
        }
    }
}


@Preview
@Composable
private fun TopPreview() {
    DesignSystem {
        val model = BalanceSheetViewModel.State(
            amountText = "$12.34 of Kin",
            marketValue = 2_225_100.0,
            selectedRate = Rate(Currency.Kin.rate, CurrencyCode.KIN),
            chatsLoading = false,
            currencyFlag = R.drawable.ic_currency_kin,
            chats = emptyList(),
            isBucketDebuggerEnabled = false,
            isBucketDebuggerVisible = false,
        )

        BalanceTop(
            state = model,
            isClickable = true
        )
    }
}