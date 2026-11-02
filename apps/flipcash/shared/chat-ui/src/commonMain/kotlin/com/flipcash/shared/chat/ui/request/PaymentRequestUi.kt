package com.flipcash.shared.chat.ui.request

import androidx.compose.ui.platform.LocalContext
import android.content.res.Resources
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.core.R
import com.flipcash.app.core.ui.inDisplayCurrency
import com.flipcash.app.core.ui.shimmer
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.StatusLine
import com.flipcash.services.models.paymentrequest.ViewerStanding
import com.flipcash.services.models.paymentrequest.statusLine
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The time, ticking once a minute, for status lines and the ended state. One ticker per screen:
 * pass its value down rather than calling this per card.
 */
@Composable
fun rememberMinuteClock(): Instant {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.minutes)
            now = Clock.System.now()
        }
    }
    return now
}

/** A request amount as the viewer reads it: in their display currency, whole amounts without ".00". */
@Composable
fun Fiat.requestText(): String = inDisplayCurrency().formatted(Fiat.FormattingRule.Truncated)

@Composable
fun StatusLine.text(): String = text(LocalContext.current.resources)

fun StatusLine.text(resources: Resources): String = when (this) {
    is StatusLine.DaysLeft -> resources.getQuantityString(R.plurals.label_requestDaysLeft, days, days)
    is StatusLine.HoursLeft -> resources.getQuantityString(R.plurals.label_requestHoursLeft, hours, hours)
    is StatusLine.MinutesLeft -> resources.getQuantityString(R.plurals.label_requestMinutesLeft, minutes, minutes)
    StatusLine.PaidInFull -> resources.getString(R.string.label_requestPaidInFull)
    StatusLine.Closed -> resources.getString(R.string.label_requestClosed)
    StatusLine.Expired -> resources.getString(R.string.label_requestExpired)
}

/** "$20 paid, $520 requested". */
@Composable
fun PaymentRequest.progressText(): String {
    val split = split ?: return ""
    return stringResource(R.string.label_requestProgress, split.paid.requestText(), split.requested.requestText())
}

/** The paid fraction as a thin bar; grey once the request has ended. */
@Composable
fun RequestProgressBar(fraction: Float, ended: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(1.5.dp)
    val shown by animateFloatAsState(fraction, RequestMotion.change(), label = "request progress")
    val fill by animateColorAsState(
        if (ended) CodeTheme.colors.textSecondary else CodeTheme.colors.success,
        RequestMotion.change(),
        label = "request progress fill",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(CodeTheme.colors.divider, shape),
    ) {
        if (shown > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(shown.coerceIn(0f, 1f))
                    .background(fill, shape),
            )
        }
    }
}

/**
 * The request card's contents (frames 07 and 08): label, title, progress line, bar and status
 * line. [title] null draws the placeholders while the request loads. The caller supplies the
 * surface: a chat bubble in the transcript, a full-width card in the expanded deck.
 * [detailModifier] goes on the pieces the deck pill has no counterpart for (label, bar, status
 * line), so the deck can fade them apart from the card. [standing], given in the deck, puts the
 * viewer's part at the footer's trailing end and the paid badge on the glyph.
 */
@Composable
fun PaymentRequestCardContent(
    request: PaymentRequest?,
    title: String?,
    now: Instant,
    modifier: Modifier = Modifier,
    glyphModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    amountsModifier: Modifier = Modifier,
    detailModifier: Modifier = Modifier,
    standing: ViewerStanding? = null,
) {
    val loading = request == null || title == null
    val ended = request != null && !request.isOpen(now)
    val glyphFill by animateColorAsState(
        if (ended) CodeTheme.colors.textSecondary else CodeTheme.colors.success,
        RequestMotion.change(),
        label = "request glyph fill",
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Box(
                modifier = glyphModifier
                    .size(34.dp)
                    .background(glyphFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "$", style = CodeTheme.typography.textLarge, color = Color.Black)
                PaidBadge(glyph = 34.dp, ended = ended, paid = standing is ViewerStanding.Paid)
            }
            Column {
                Text(
                    text = stringResource(R.string.label_paymentRequest_card),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                    modifier = detailModifier,
                )
                Text(
                    text = title.orEmpty(),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (loading) Modifier.width(120.dp).shimmer(RoundedCornerShape(4.dp)) else titleModifier,
                )
            }
        }
        Text(
            text = request?.takeUnless { loading }?.progressText().orEmpty(),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textMain.copy(alpha = 0.75f),
            modifier = if (loading) Modifier.width(160.dp).shimmer(RoundedCornerShape(4.dp)) else amountsModifier,
        )
        RequestProgressBar(fraction = request?.split?.paidFraction ?: 0f, ended = ended, modifier = detailModifier)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Text(
                text = request?.takeUnless { loading }?.statusLine(now)?.text().orEmpty(),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
                modifier = if (loading) Modifier.width(100.dp).shimmer(RoundedCornerShape(4.dp)) else detailModifier,
            )
            val viewer = standing?.takeUnless { loading }?.text()
            if (viewer != null) {
                Spacer(Modifier.weight(1f))
                // The viewer's part cross-fades when it changes, as a share turns paid.
                Crossfade(targetState = viewer, animationSpec = RequestMotion.change(), modifier = detailModifier, label = "viewer standing") { text ->
                    Text(
                        text = text,
                        style = CodeTheme.typography.caption,
                        color = when (standing) {
                            is ViewerStanding.Paid, is ViewerStanding.Owes -> CodeTheme.colors.success
                            else -> CodeTheme.colors.textSecondary
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
