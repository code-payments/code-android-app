package com.getcode.view.main.balance

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import com.getcode.App
import com.getcode.R
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.manager.AnalyticsManager
import com.getcode.model.AirdropType
import com.getcode.model.Currency
import com.getcode.model.HistoricalTransaction
import com.getcode.model.PaymentType
import com.getcode.theme.*
import com.getcode.util.CurrencyUtils
import com.getcode.util.RepeatOnLifecycle
import com.getcode.view.components.*
import com.getcode.view.main.account.AccountDebugBuckets
import com.getcode.view.main.giveKin.AmountArea
import com.getcode.view.previewComponent.PreviewColumn
import timber.log.Timber


@Composable
fun BalanceSheet(
    viewModel: BalanceSheetViewModel = hiltViewModel(),
    upPress: () -> Unit = {},
    faqOpen: () -> Unit = {},
) {
    val dataState by viewModel.uiFlow.collectAsState()

    RepeatOnLifecycle(targetState = Lifecycle.State.RESUMED) {
        viewModel.reset()
    }

    AnalyticsScreenWatcher(
        lifecycleOwner = LocalLifecycleOwner.current,
        event = AnalyticsManager.Screen.Balance
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight)
    ) {
        SheetTitle(
            modifier = Modifier.padding(horizontal = 20.dp),
            title = stringResource(R.string.title_balance),
            onCloseIconClicked = upPress,
            onBackIconClicked = { viewModel.setDebugBucketsVisible(false) },
            closeButton = !dataState.isDebugBucketsVisible,
            backButton = dataState.isDebugBucketsVisible,
        )
        if (dataState.isDebugBucketsVisible) {
            AccountDebugBuckets()
        } else {
            BalanceContent(viewModel, dataState, upPress, faqOpen)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BalanceContent(
    viewModel: BalanceSheetViewModel,
    dataState: BalanceSheetUiModel,
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

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        state = lazyListState
    ) {
        val transactionsEmpty =
                dataState.historicalTransactionsUiModel.isEmpty()

        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 35.dp)
                    .padding(bottom = 10.dp)
                    .fillParentMaxWidth()
                    .graphicsLayer { translationY = firstItemTranslationY },
            ) {
                BalanceTop(
                    dataState,
                    dataState.isDebugBucketsEnabled
                ) {
                    viewModel.setDebugBucketsVisible(true)
                }
                if (!transactionsEmpty) {
                    KinValueHint(upPress, faqOpen)
                }
            }
        }
        items(dataState.historicalTransactionsUiModel, key = { it.id }) { event ->
            Row(Modifier.animateItemPlacement()) {
                TransactionItem(event)
            }
        }

        if (transactionsEmpty) {
            item {
                EmptyTransactionsHint(upPress, faqOpen)
            }
        }
    }
}

@Composable
fun TransactionItem(event: HistoricalTransactionUIModel) {

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
                style = MaterialTheme.typography.body1
            )
            Text(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(top = 3.dp),
                text = event.dateText,
                color = BrandLight,
                style = MaterialTheme.typography.body2
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
                    style = MaterialTheme.typography.body1
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
                        style = MaterialTheme.typography.body1,
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
    dataState: BalanceSheetUiModel,
    isClickable: Boolean,
    onClick: () -> Unit = {}
) {
    AmountArea(
        amountText = dataState.amountText,
        isAltCaption = false,
        isAltCaptionKinIcon = false,
        currencyResId = dataState.selectedCurrency?.resId,
        isClickable = isClickable,
        onClick = onClick
    )
}

@Composable
private fun ColumnScope.KinValueHint(upPress: () -> Unit, faqOpen: () -> Unit) {
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
            style = MaterialTheme.typography.body1,
            onClick = {
                annotatedBalanceString
                    .getStringAnnotations(
                        App.getInstance().getString(R.string.subtitle_learnMore),
                        it,
                        it
                    )
                    .firstOrNull()?.let {
                        upPress()
                        faqOpen()
                    }
            }
        )
    }
}

@Composable
private fun EmptyTransactionsHint(upPress: () -> Unit, faqOpen: () -> Unit) {
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
                style = MaterialTheme.typography.body1
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
                tag = App.getInstance().getString(R.string.title_faq),
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
                style = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center),
                onClick = {
                    annotatedLinkString
                        .getStringAnnotations(
                            App.getInstance().getString(R.string.title_faq),
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
    val model = BalanceSheetUiModel(
        amountText = "$12.34 of Kin",
        marketValue = 1.0,
        selectedCurrency = CurrencyUtils.currencyKin,
        historicalTransactions = emptyList(),
        historicalTransactionsUiModel = emptyList(),
        isDebugBucketsEnabled =false,
        isDebugBucketsVisible = false,
    )

    BalanceTop(
        dataState = model,
        isClickable = false)
}

@Preview
@Composable
private fun ItemPreview() {
    val transaction = HistoricalTransactionUIModel(
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



