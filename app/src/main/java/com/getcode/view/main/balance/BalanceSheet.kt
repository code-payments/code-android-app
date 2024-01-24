package com.getcode.view.main.balance

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.data.transactions.HistoricalTransactionUiModel
import com.getcode.model.AirdropType
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.PaymentType
import com.getcode.model.Rate
import com.getcode.model.Title
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.FaqScreen
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White10
import com.getcode.theme.extraSmall
import com.getcode.util.DateUtils
import com.getcode.util.Kin
import com.getcode.util.rememberedClickable
import com.getcode.view.components.CodeCircularProgressIndicator
import com.getcode.view.components.chat.ChatNode
import com.getcode.view.main.account.AccountDebugBuckets
import com.getcode.view.main.giveKin.AmountArea
import com.getcode.view.main.giveKin.AmountText
import com.getcode.view.previewComponent.PreviewColumn


@Composable
fun BalanceSheet(
    state: BalanceSheetViewModel.State,
    dispatch: (BalanceSheetViewModel.Event) -> Unit,
) {
    val navigator = LocalCodeNavigator.current


    AnimatedContent(
        targetState = state.isDebugBucketsVisible,
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
            AccountDebugBuckets()
        } else {
            BalanceContent(
                state = state,
                dispatch = dispatch,
                upPress = { navigator.hide() },
                faqOpen = { navigator.push(FaqScreen) }
            )
        }
    }
}

@Composable
fun BalanceContent(
    state: BalanceSheetViewModel.State,
    dispatch: (BalanceSheetViewModel.Event) -> Unit,
    upPress: () -> Unit,
    faqOpen: () -> Unit
) {
    val lazyListState = rememberLazyListState()

    val transactionsEmpty by remember(state.chats) {
        derivedStateOf { state.chats.isEmpty() }
    }

    val canClickBalance by remember(state.isDebugBucketsEnabled) {
        derivedStateOf { state.isDebugBucketsVisible }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth(),
        state = lazyListState
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(
                        horizontal = CodeTheme.dimens.inset,
                        vertical = CodeTheme.dimens.grid.x7
                    )
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .fillParentMaxWidth(),
            ) {
                BalanceTop(
                    state,
                    canClickBalance,
                ) {
                    dispatch(BalanceSheetViewModel.Event.OnDebugBucketsVisible(true))
                }
                if (!transactionsEmpty && !state.chatsLoading) {
                    KinValueHint(faqOpen)
                }
            }
        }
        itemsIndexed(
            state.chats,
            key = { _, item -> item.id },
            contentType = { _, item -> item }) { index, event ->
            ChatNode(chat = event) {

            }
            if (index < state.chats.lastIndex) {
                Divider(
                    modifier = Modifier.padding(start = CodeTheme.dimens.inset),
                    color = White10,
                )
            }
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
                            text = "Loading your balance and transaction history",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            transactionsEmpty -> {
                item {
                    EmptyTransactionsHint(upPress, faqOpen)
                }
            }
        }
    }
}

@Composable
fun TransactionItem(event: HistoricalTransactionUiModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CodeTheme.dimens.grid.x12)
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .align(Alignment.CenterStart)
                .padding(horizontal = CodeTheme.dimens.inset)
        ) {
            Text(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(bottom = CodeTheme.dimens.grid.x1),
                text = when (event.paymentType) {
                    PaymentType.Send ->
                        when {
                            event.isWithdrawal -> stringResource(R.string.title_withdrewKin)
                            event.isRemoteSend -> stringResource(R.string.title_sent)
                            else -> stringResource(R.string.title_gaveKin)
                        }

                    PaymentType.Receive ->
                        when {
                            event.airdropType == AirdropType.GiveFirstKin -> stringResource(R.string.title_referralBonus)
                            event.airdropType == AirdropType.GetFirstKin -> stringResource(R.string.title_welcomeBonus)
                            event.isDeposit -> stringResource(R.string.title_deposited)
                            event.isRemoteSend && event.isReturned -> stringResource(R.string.title_returned)
                            else -> stringResource(R.string.title_received)
                        }

                    else -> stringResource(R.string.title_unknown)
                },
                style = CodeTheme.typography.body1
            )
            Text(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(top = CodeTheme.dimens.grid.x1),
                text = event.dateText,
                color = BrandLight,
                style = CodeTheme.typography.body2
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(horizontal = CodeTheme.dimens.inset),
            horizontalAlignment = Alignment.End
        ) {
            Row {
                event.currencyResourceId?.let {
                    Image(
                        modifier = Modifier
                            .padding(end = CodeTheme.dimens.grid.x2)
                            .size(CodeTheme.dimens.grid.x3)
                            .clip(CodeTheme.shapes.extraSmall)
                            .align(CenterVertically),
                        painter = painterResource(id = event.currencyResourceId),
                        contentDescription = ""
                    )
                }
                Text(
                    text = event.amountText,
                    style = CodeTheme.typography.body1
                )
            }
            if (!event.isKin) {
                Row(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3)
                ) {
                    Image(
                        modifier = Modifier
                            .padding(end = CodeTheme.dimens.grid.x1)
                            .size(CodeTheme.dimens.staticGrid.x2)
                            .align(CenterVertically),
                        painter = painterResource(id = R.drawable.ic_kin_brand),
                        contentDescription = ""
                    )
                    Text(
                        text = event.kinAmountText,
                        style = CodeTheme.typography.body1,
                        color = BrandLight
                    )
                }
            }
        }

        Divider(
            color = White10,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .height(0.5.dp)
                .align(BottomCenter)
        )
    }
}

@Composable
fun BalanceTop(
    state: BalanceSheetViewModel.State,
    isClickable: Boolean,
    onClick: () -> Unit = {}
) {
    if (!state.chatsLoading) {
        AmountText(
            modifier = Modifier.rememberedClickable(enabled = isClickable) { onClick() },
            currencyResId = state.currencyFlag,
            amountText = state.amountText
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
                style = SpanStyle(color = BrandLight),
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
            style = CodeTheme.typography.body1,
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
private fun EmptyTransactionsHint(upPress: () -> Unit, faqOpen: () -> Unit) {
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
                color = BrandLight,
                style = CodeTheme.typography.body1
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
                    color = BrandLight,
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
                style = CodeTheme.typography.body1.copy(textAlign = TextAlign.Center),
                onClick = {
                    annotatedLinkString
                        .getStringAnnotations(
                            context.getString(R.string.title_faq),
                            it,
                            it
                        )
                        .firstOrNull()?.let { _ ->
                            upPress()
                            faqOpen()
                        }
                }
            )
        }
    }
}


@Preview
@Composable
private fun TopPreview() {
    val model = BalanceSheetViewModel.State(
        amountText = "$12.34 of Kin",
        marketValue = 1.0,
        selectedRate = Rate(Currency.Kin.rate, CurrencyCode.KIN),
        chatsLoading = false,
        chats = emptyList(),
        isDebugBucketsEnabled = false,
        isDebugBucketsVisible = false,
    )

    BalanceTop(
        state = model,
        isClickable = false
    )
}

@Preview
@Composable
private fun ItemPreview() {
    val transaction = HistoricalTransactionUiModel(
        id = emptyList(),
        amountText = "$1.23 of Kin",
        dateText = "2023-10-10 10:10 p.m.",
        isKin = false,
        kinAmountText = "1,234",
        currencyResourceId = R.drawable.ic_currency_kin,
        paymentType = PaymentType.Send,
        isDeposit = true,
        isWithdrawal = false,
        isRemoteSend = false,
        isReturned = false,
        airdropType = null
    )

    PreviewColumn {
        TransactionItem(event = transaction)
    }
}

@Composable
operator fun Title?.invoke(): String = when (this) {
    is Title.Domain -> this.value
    is Title.Localized -> this.value
    else -> ""
}


