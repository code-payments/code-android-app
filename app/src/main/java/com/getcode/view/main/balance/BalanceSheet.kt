package com.getcode.view.main.balance

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.App
import com.getcode.R
import com.getcode.data.transactions.HistoricalTransactionUiModel
import com.getcode.model.AirdropType
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.PaymentType
import com.getcode.model.Rate
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.FaqScreen
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.util.Kin
import com.getcode.view.components.CodeCircularProgressIndicator
import com.getcode.view.main.account.AccountDebugBuckets
import com.getcode.view.main.giveKin.AmountArea
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BalanceContent(
    state: BalanceSheetViewModel.State,
    dispatch: (BalanceSheetViewModel.Event) -> Unit,
    upPress: () -> Unit,
    faqOpen: () -> Unit
) {
    val lazyListState = rememberLazyListState()

    val firstItemTranslationY by remember {
        derivedStateOf {
            when {
                lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty() && lazyListState.firstVisibleItemIndex == 0 ->
                    lazyListState.firstVisibleItemScrollOffset * .2f

                else -> 0f
            }
        }
    }

    val transactionsEmpty by remember(state.historicalTransactions) {
        derivedStateOf { state.historicalTransactions.isEmpty() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        state = lazyListState
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 35.dp)
                    .padding(bottom = 10.dp)
                    .fillParentMaxWidth()
                    .graphicsLayer { translationY = firstItemTranslationY },
            ) {
                BalanceTop(
                    state,
                    state.isDebugBucketsEnabled
                ) {
                    dispatch(BalanceSheetViewModel.Event.OnDebugBucketsVisible(true))
                }
                if (!transactionsEmpty && !state.historicalTransactionsLoading) {
                    KinValueHint(faqOpen)
                }
            }
        }
        items(state.historicalTransactions, key = { it.id }) { event ->
            Row(Modifier.animateItemPlacement()) {
                TransactionItem(event)
            }
        }

        when {
            state.historicalTransactionsLoading -> {
                item {
                    Box(modifier = Modifier.fillParentMaxSize()) {
                        CodeCircularProgressIndicator(modifier = Modifier.align(TopCenter))
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
            .height(80.dp)
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .align(Alignment.CenterStart)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(bottom = 3.dp),
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
                    .padding(top = 3.dp),
                text = event.dateText,
                color = BrandLight,
                style = CodeTheme.typography.body2
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row {
                event.currencyResourceId?.let {
                    Image(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(13.dp)
                            .clip(RoundedCornerShape(6.dp))
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
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Image(
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .height(10.dp)
                            .width(10.dp)
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
                .padding(horizontal = 20.dp)
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
    AmountArea(
        amountText = state.amountText,
        isAltCaption = false,
        isAltCaptionKinIcon = false,
        currencyResId = state.currencyFlag,
        isClickable = isClickable,
        onClick = onClick
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
                .padding(horizontal = 3.dp),
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
            .padding(horizontal = 30.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier
                .align(CenterHorizontally)
        ) {
            Text(
                modifier = Modifier.padding(vertical = 5.dp),
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
        historicalTransactions = emptyList(),
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



