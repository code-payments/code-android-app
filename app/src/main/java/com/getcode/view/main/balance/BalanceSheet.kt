package com.getcode.view.main.balance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
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
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import com.getcode.model.chat.Chat
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.BuyMoreKinModal
import com.getcode.navigation.screens.NotificationCollectionScreen
import com.getcode.navigation.screens.CurrencySelectionModal
import com.getcode.navigation.screens.FaqScreen
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.components.chat.ChatNode
import com.getcode.utils.Kin
import com.getcode.view.main.account.BucketDebugger
import com.getcode.view.main.currency.CurrencySelectKind
import com.getcode.view.main.giveKin.AmountArea


@Composable
fun BalanceScreen(
    state: BalanceSheetViewModel.State,
    dispatch: (BalanceSheetViewModel.Event) -> Unit,
) {
    val navigator = LocalCodeNavigator.current

    AnimatedContent(
        targetState = state.isBucketDebuggerVisible,
        label = "show/hide buckets",
        transitionSpec = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End
            ) togetherWith slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start
            )
        }
    ) { buckets ->
        if (buckets) {
            BucketDebugger()
        } else {
            BalanceContent(
                state = state,
                dispatch = dispatch,
                faqOpen = { navigator.push(FaqScreen) },
                openChat = { navigator.push(NotificationCollectionScreen(it.id)) },
                buyMoreKin = { navigator.push(BuyMoreKinModal()) }
            )
        }
    }
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
                ) {
                    navigator.push(CurrencySelectionModal(CurrencySelectKind.Local))
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
            ) {
                if (!chatsEmpty && !state.chatsLoading) {
                    KinValueHint(faqOpen)
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x11),
            ) {
                if (!chatsEmpty && !state.chatsLoading && state.buyModule.enabled) {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CodeTheme.dimens.grid.x5),
                        buttonState = ButtonState.Filled,
                        onClick = {
                            if (state.buyModule.available) {
                                buyMoreKin()
                            } else {
                                TopBarManager.showMessage(
                                    TopBarManager.TopBarMessage(
                                        title = context.getString(R.string.error_title_buyModuleUnavailable),
                                        message = context.getString(R.string.error_description_buyModuleUnavailable),
                                        type = TopBarManager.TopBarMessageType.ERROR
                                    )
                                )
                            }
                        },
                        text = stringResource(id = R.string.action_addCash)
                    )
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

            chatsEmpty -> {
                item {
                    EmptyTransactionsHint(faqOpen)
                }
            }
        }
    }
}

@Composable
fun BalanceTop(
    state: BalanceSheetViewModel.State,
    isClickable: Boolean,
    onClick: () -> Unit = {}
) {
    AmountArea(
        amountText = state.amountText,
        isAltCaption = false,
        isAltCaptionKinIcon = false,
        isLoading = state.chatsLoading,
        currencyResId = state.currencyFlag,
        isClickable = isClickable,
        onClick = onClick,
        textStyle = CodeTheme.typography.displayLarge,
    )
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
    CodeTheme {
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
