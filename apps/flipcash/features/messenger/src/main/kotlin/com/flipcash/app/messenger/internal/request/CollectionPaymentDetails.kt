package com.flipcash.app.messenger.internal.request

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.flipcash.app.core.ui.TokenIcon
import com.flipcash.app.tokens.CollectionFundingFormatter
import com.flipcash.app.tokens.CollectionFundingStrings
import com.flipcash.app.tokens.CollectionFundingPlan
import com.flipcash.app.tokens.PaymentDetailsRow
import com.flipcash.app.tokens.paymentDetailsOrder
import com.getcode.opencode.internal.extensions.fractionDigits
import com.flipcash.features.messenger.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle

/** What a funded collection's pay sheet draws about its conversions. */
internal data class CollectionFundingUi(
    /** The line under the amount, "plus ~$0.02 of conversion fees"; null when none is drawn. */
    val modalFee: String?,
    val details: PaymentDetailsUi,
    /** The short state in place of the swipe; null when the balances cover the payment. */
    val short: CollectionShortUi? = null,
)

/** Frames 4.2, 7.2, 8.2: "You’re $2.04 Short", "Add $5.00 and Pay $11.00", and the minimum. */
internal data class CollectionShortUi(
    val shortBy: String,
    val addButton: String,
    val footnote: String,
    /**
     * The top-up reads the same as the amount, as when nothing funds it and no minimum applies:
     * the Request screen's button then reads "Pay $10.00" (frame 1.1), not [addButton] (2.1).
     */
    val topUpIsAmount: Boolean = false,
)

/** Payment Details (frames 5.3, 6.5, 9.3), each currency row paired with its token's icon. */
internal data class PaymentDetailsUi(
    val pay: PaymentDetailsTokenRow,
    val use: List<PaymentDetailsTokenRow>,
    val total: PaymentDetailsRow,
)

internal data class PaymentDetailsTokenRow(val token: Token?, val row: PaymentDetailsRow)

/**
 * Formats [plan] for the pay sheet with the shared [CollectionFundingFormatter], so the fee line and
 * Payment Details read as the group-collections spec's "Fee display" and its vectors do.
 *
 * A plan that needs a top-up is the plan after the top-up, so its fee line and Payment Details
 * are what paying after the top-up costs (7.3, 8.2), and [CollectionFundingUi.short] carries the
 * short state with [topUpFootnote] under its button.
 *
 * @param tokenOf a planner id's token, or null when the wallet does not know it.
 * @param nameOf a token's display name ("Dollars" for the reserve).
 * @param strings the text around each amount, from string resources.
 */
internal fun collectionFundingUi(
    plan: CollectionFundingPlan,
    amount: Fiat,
    tokenOf: (String) -> Token?,
    nameOf: (Token) -> String,
    topUpSourceName: String,
    topUpFootnote: String,
    strings: CollectionFundingStrings,
): CollectionFundingUi {
    val display = CollectionFundingFormatter(
        currency = amount.currencyCode,
        minorDigits = amount.currencyCode.fractionDigits,
        topUpSourceName = topUpSourceName,
        strings = strings,
    ).display(plan, amount.quarks) { id -> tokenOf(id)?.let(nameOf) ?: id }
    // display lists "You'll Use" in paymentDetailsOrder, one row per leg.
    val use = plan.paymentDetailsOrder().zip(display.details.use) { leg, row ->
        PaymentDetailsTokenRow(tokenOf(leg.source), row)
    }
    return CollectionFundingUi(
        modalFee = display.modalFee,
        details = PaymentDetailsUi(
            pay = PaymentDetailsTokenRow(tokenOf(plan.payingCurrency), display.details.pay),
            use = use,
            total = display.details.total,
        ),
        short = display.shortBy?.let { shortBy ->
            display.addButton?.let { addButton ->
                CollectionShortUi(shortBy, addButton, topUpFootnote, topUpIsAmount = display.topUp == display.details.pay.amount)
            }
        },
    )
}

/**
 * The fee line between the amount and the pill (frames 5.2, 6.4, 9.2): the fee text, then ⓘ.
 * Tapping it opens Payment Details.
 */
@Composable
internal fun CollectionFeeLine(fee: String, onDetails: () -> Unit) {
    PaymentDetailsEntryLine(
        text = fee,
        style = CodeTheme.typography.caption,
        color = CodeTheme.colors.textSecondary,
        iconSize = CodeTheme.dimens.staticGrid.x3,
        onDetails = onDetails,
        modifier = Modifier.testTag(COLLECTION_FEE_LINE_TAG),
    )
}

/**
 * The short line in the swipe's place (frames 4.2, 7.2, 8.2): "You’re $2.04 Short", then ⓘ as on
 * the fee line. Tapping it opens Payment Details, the only way to reach 7.3, since 7.2 draws no
 * fee line. The frames don't draw this ⓘ; the group-collections spec adds it (Open with design
 * item 9).
 */
@Composable
internal fun CollectionShortLine(shortBy: String, onDetails: () -> Unit) {
    PaymentDetailsEntryLine(
        text = shortBy,
        // 7.2 (node 13966:122) draws it 21pt Bold; textLarge is the nearest size.
        style = CodeTheme.typography.textLarge.copy(fontWeight = FontWeight.Bold),
        color = CodeTheme.colors.textMain,
        // The fee line's ⓘ is 15dp beside 12sp; this keeps that ratio beside 20sp.
        iconSize = CodeTheme.dimens.staticGrid.x5,
        onDetails = onDetails,
        modifier = Modifier.testTag(COLLECTION_SHORT_LINE_TAG),
    )
}

/**
 * [text], then ⓘ, the whole line one tap target that opens Payment Details. The fee line and the
 * short line share it, so both carry the same click label and ⓘ description.
 */
@Composable
private fun PaymentDetailsEntryLine(
    text: String,
    style: TextStyle,
    color: Color,
    iconSize: Dp,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.title_paymentDetails)
    Row(
        modifier = modifier.clickable(onClickLabel = label, role = Role.Button, onClick = onDetails),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        Text(
            text = text,
            // Wraps before it pushes the ⓘ off the line.
            modifier = Modifier.weight(1f, fill = false),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
        )
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = label,
            modifier = Modifier.size(iconSize),
            tint = color,
        )
    }
}

/**
 * Payment Details (frames 5.3, 6.5, 9.3, nodes 13952:149, 14002:320, 14012:378): back, "You’ll
 * Pay" and its row, "You’ll Use" and one row per currency drawn from, a divider, then "Total".
 * Pushed inside the pay sheet, in place of its content; the back arrow calls [onBack].
 */
@Composable
internal fun PaymentDetailsPage(details: PaymentDetailsUi, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag(PAYMENT_DETAILS_SHEET_TAG),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_paymentDetails),
            titleTextStyle = CodeTheme.typography.textLarge,
            // A leading back, as the frames draw it. onBackIconClicked would trade it for a
            // trailing Close under a sheet navigator's root.
            leadingContent = { AppBarDefaults.UpNavigation(onClick = onBack) },
        )
        // 9.3 lists 29 currencies, more than a sheet holds.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x4),
        ) {
            DetailsHeading(stringResource(R.string.subtitle_youllPay))
            DetailsRow(details.pay, Modifier.testTag(PAYMENT_DETAILS_PAY_ROW_TAG))
            DetailsHeading(stringResource(R.string.subtitle_youllUse))
            details.use.forEach { DetailsRow(it, Modifier.testTag(PAYMENT_DETAILS_USE_ROW_TAG)) }
            HorizontalDivider(
                modifier = Modifier.padding(start = IconSize + RowGap),
                color = CodeTheme.colors.divider,
            )
            DetailsLine(
                modifier = Modifier.testTag(PAYMENT_DETAILS_TOTAL_TAG),
                leading = { Spacer(Modifier.size(IconSize)) },
                name = { Text(details.total.name, style = Emphasis, color = CodeTheme.colors.textMain) },
                amount = details.total.amount,
            )
        }
    }
}

@Composable
private fun DetailsHeading(text: String) {
    Text(
        modifier = Modifier.padding(top = CodeTheme.dimens.grid.x4, bottom = CodeTheme.dimens.grid.x1),
        text = text,
        style = Emphasis,
        color = CodeTheme.colors.textMain,
    )
}

@Composable
private fun DetailsRow(row: PaymentDetailsTokenRow, modifier: Modifier = Modifier) {
    DetailsLine(
        modifier = modifier,
        leading = {
            row.token?.let { TokenIcon(it, Modifier.size(IconSize)) } ?: Spacer(Modifier.size(IconSize))
        },
        name = {
            Column {
                Text(row.row.name, style = CodeTheme.typography.textMedium, color = CodeTheme.colors.textMain)
                row.row.sublabels.forEach {
                    Text(it, style = CodeTheme.typography.caption, color = CodeTheme.colors.textSecondary)
                }
            }
        },
        amount = row.row.amount,
    )
}

@Composable
private fun DetailsLine(
    modifier: Modifier,
    leading: @Composable () -> Unit,
    name: @Composable () -> Unit,
    amount: String,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        leading()
        Column(Modifier.weight(1f)) { name() }
        Text(amount, style = Emphasis, color = CodeTheme.colors.textMain)
    }
}

private val IconSize @Composable get() = CodeTheme.dimens.staticGrid.x8
private val RowGap @Composable get() = CodeTheme.dimens.grid.x3
private val Emphasis @Composable get() = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.SemiBold)

internal const val COLLECTION_FEE_LINE_TAG = "collection_fee_line"
internal const val COLLECTION_SHORT_LINE_TAG = "collection_short_line"
internal const val PAYMENT_DETAILS_SHEET_TAG = "payment_details_sheet"
internal const val PAYMENT_DETAILS_PAY_ROW_TAG = "payment_details_pay"
internal const val PAYMENT_DETAILS_USE_ROW_TAG = "payment_details_use"
internal const val PAYMENT_DETAILS_TOTAL_TAG = "payment_details_total"
