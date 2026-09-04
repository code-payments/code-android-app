package com.flipcash.shared.transactionhistory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.app.core.ui.FlagWithFiat
import com.flipcash.app.core.ui.ReceiptLineItem
import com.flipcash.app.core.ui.TokenIconWithName
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.extraSmall
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.format
import java.util.Locale

/**
 * The transaction details screen (Figma 9708:105260) — what opens when an activity row, a feed
 * item, or a recents entry is tapped.
 *
 * Stateless: everything it draws is resolved in [TransactionDetails], so it renders identically
 * from live data and from a screenshot test.
 *
 * The header restates the entry the way the user would: the row's own avatar, then who or what it
 * was ("Sally The Streamer", "Withdraw"), then its other side where the heading hasn't already said
 * it ("In Person", "with Dollars" — see [TransactionSubtitles]), then how much and in which
 * direction, then when and in which token. The nav title stays the literal "Details", so the entry
 * has to name itself in the header rather than the bar.
 *
 * Cancelling is the bar's end action rather than a control at the foot of the scroll: it applies to
 * the whole entry, not to anything in the receipt, and putting it below a variable-length card
 * meant it landed in a different place on every kind — and sometimes below the fold.
 *
 * [iconOverride] threads through to the token images for previews and screenshot tests, where the
 * remote URL never resolves.
 */
@Composable
fun TransactionDetailsContent(
    details: TransactionDetails,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { },
    onCopyId: () -> Unit = { },
    onViewInChat: () -> Unit = { },
    onCancel: () -> Unit = { },
    iconOverride: @Composable ((Any?) -> Any?) = { it },
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CodeTheme.colors.background),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_txnDetails),
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = onBack,
            endContent = {
                if (details.canCancel) {
                    Text(
                        modifier = Modifier
                            .unboundedClickable(onClick = onCancel)
                            .padding(horizontal = CodeTheme.dimens.grid.x2),
                        text = stringResource(R.string.action_txnDetails_cancel),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.error,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x6),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Header(details = details, iconOverride = iconOverride)

            DetailCard(details = details)

            IdCard(id = details.id, onCopyId = onCopyId)

            if (details.canViewInChat) {
                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_txnDetails_viewInChat),
                    buttonState = ButtonState.Filled10,
                    onClick = onViewInChat,
                )
            }
        }
    }
}

/**
 * Two stacked blocks, as Figma 9708:117414 draws them: who or what it was, then how much and when.
 * Lines sit one grid step apart within a block and two between them, which is why this is a pair of
 * nested columns rather than one evenly-spaced stack — the wider gap either side of the amount is
 * what separates the two facts.
 *
 * Image sizes come off [com.getcode.theme.Dimensions.staticGrid] so the avatar and the icons keep
 * their size across width classes; the gaps around them come off the responsive `grid`, like the
 * rest of the screen's spacing.
 */
@Composable
private fun Header(
    details: TransactionDetails,
    modifier: Modifier = Modifier,
    iconOverride: @Composable ((Any?) -> Any?) = { it },
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = CodeTheme.dimens.grid.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            TransactionAvatarImage(
                avatar = details.avatar,
                size = CodeTheme.dimens.staticGrid.x16,
                slotSize = CodeTheme.dimens.staticGrid.x18,
                badgeSize = CodeTheme.dimens.staticGrid.x6,
                iconOverride = iconOverride,
            )

            // A person-to-person entry is titled by the person; everything else by what it was.
            Text(
                text = details.heading?.takeIf { it.isNotBlank() }
                    ?: stringResource(details.kind.headingRes),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center,
            )

            // The other side of the movement, for the kinds whose heading doesn't already carry
            // it — see [TransactionSubtitles].
            details.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            details.amount?.let { amount ->
                FlagWithFiat(
                    fiat = amount,
                    extraPrefix = details.signedAmountPrefix?.ifBlank { null },
                    iconSize = CodeTheme.dimens.staticGrid.x5,
                    spacing = CodeTheme.dimens.grid.x1,
                    textStyle = CodeTheme.typography.displaySmall,
                )
            }

            // When, and in which token — the mint is what "$20.00" alone never says.
            Row(
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatActivityTimestamp(details.timestamp),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
                details.token?.let { token ->
                    // A drawn dot, not a "•" glyph: the glyph's size and its offset from the
                    // baseline are the font's to decide, and the design wants a small circle
                    // centred on the line.
                    Box(
                        modifier = Modifier
                            .size(CodeTheme.dimens.staticGrid.x1)
                            .background(CodeTheme.colors.textSecondary, CircleShape),
                    )
                    TokenIconWithName(
                        token = token,
                        imageSize = CodeTheme.dimens.staticGrid.x3,
                        iconOverride = iconOverride,
                        textStyle = CodeTheme.typography.textSmall,
                        textColor = CodeTheme.colors.textSecondary,
                        spacing = CodeTheme.dimens.grid.x1,
                    )
                }
            }
        }
    }
}

/**
 * The receipt rows. A non-financial entry (unknown metadata, no amount) has no currency, rate or
 * token quantity to state, so the card collapses to when-and-what-state rather than showing empty
 * rows.
 */
@Composable
private fun DetailCard(details: TransactionDetails, modifier: Modifier = Modifier) {
    DetailsCard(modifier = modifier) {
        details.currencyCode?.let {
            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.label_txnDetails_currency),
                amount = it,
            )
        }
        details.exchangeRate?.let {
            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.label_txnDetails_exchangeRate),
                amount = String.format(Locale.US, "%.6f", it),
            )
        }
        ReceiptLineItem(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.label_txnDetails_date),
            amount = details.timestamp.format("M/d/yyyy h:mm a"),
        )
        details.tokenAmount?.let {
            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.label_txnDetails_tokens),
                amount = it,
            )
        }
        // Where it went, or where it came from. Shortened to its two ends, like the id above it.
        details.account?.let { account ->
            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(account.direction.labelRes),
                amount = account.shortAddress,
            )
        }
        details.fee?.let {
            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.label_txnDetails_fee),
                amount = it.formatted(),
            )
        }
        details.received?.let {
            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.label_txnDetails_received),
                amount = it.formatted(),
            )
        }
        ReceiptLineItem(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.label_txnDetails_status),
            amount = stringResource(details.status.labelRes),
        )
    }
}

/**
 * The entry's id, in its own card so the copy control has an obvious target. The id is long and
 * meaningless to read, so it middle-ellipsizes — both ends stay legible, which is what someone
 * eyeballing it against a support ticket actually compares.
 */
@Composable
private fun IdCard(id: String, onCopyId: () -> Unit, modifier: Modifier = Modifier) {
    DetailsCard(modifier = modifier.unboundedClickable(onClick = onCopyId)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.label_txnDetails_id),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
            Text(
                modifier = Modifier.weight(1f),
                text = id,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                textAlign = TextAlign.End,
            )
            Icon(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = stringResource(R.string.action_txnDetails_copyId),
                tint = CodeTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * The panel both cards sit in (Figma 9708:117417) — a tint over the background and a small radius,
 * no outline: the cards are the only things on the background, so a border would draw a boundary
 * the fill already draws.
 */
@Composable
private fun DetailsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(White05, CodeTheme.shapes.extraSmall)
            .padding(CodeTheme.dimens.grid.x3),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        content = content,
    )
}
