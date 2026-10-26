package com.flipcash.shared.chat.ui.request

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.flipcash.core.R
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.ViewerStanding
import com.getcode.theme.CodeTheme

/**
 * The viewer's part of a deck card's status line: "✓ You paid $5", "You owe $5", "Your request",
 * or null with no share.
 */
@Composable
fun ViewerStanding.text(): String? = when (this) {
    is ViewerStanding.Paid -> stringResource(R.string.label_requestYouPaid, share.requestText())
    is ViewerStanding.Owes -> stringResource(R.string.label_requestYouOwe, share.requestText())
    ViewerStanding.Creator -> stringResource(R.string.label_requestYourRequest)
    ViewerStanding.NoShare -> null
}

/** For the screen reader: "you paid $5", "you owe $5", "your request", or null with no share. */
@Composable
fun ViewerStanding.description(): String? = when (this) {
    is ViewerStanding.Paid -> stringResource(R.string.cd_requestYouPaid, share.requestText())
    is ViewerStanding.Owes -> stringResource(R.string.cd_requestYouOwe, share.requestText())
    ViewerStanding.Creator -> stringResource(R.string.cd_requestYourRequest)
    ViewerStanding.NoShare -> null
}

/**
 * A deck card's status line: the viewer's standing, then " · <paid> of <requested>". A paid or
 * owed share reads in the success color. When the whole line doesn't fit, the totals go first and
 * then the standing ellipsizes.
 */
@Composable
fun ViewerStatusLine(
    request: PaymentRequest,
    standing: ViewerStanding,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val split = request.split ?: return
    val totals = stringResource(R.string.label_requestDeckProgress, split.paid.requestText(), split.requested.requestText())
    val viewer = standing.text()
    val accent = when (standing) {
        is ViewerStanding.Paid, is ViewerStanding.Owes -> CodeTheme.colors.success
        else -> color
    }
    if (viewer == null) {
        Text(text = totals, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val full = buildAnnotatedString {
        withStyle(SpanStyle(color = accent)) { append(viewer) }
        append(" · ")
        append(totals)
    }
    val short = buildAnnotatedString { withStyle(SpanStyle(color = accent)) { append(viewer) } }
    FirstThatFits(full, short, style, color, modifier)
}

/**
 * [full] when it fits on one line, otherwise [short], ellipsized. A plain layout rather than a
 * subcompose one, because the deck's pill sizes itself to this line's intrinsic width.
 */
@Composable
internal fun FirstThatFits(
    full: AnnotatedString,
    short: AnnotatedString,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            Text(text = full, style = style, color = color, maxLines = 1, softWrap = false)
            Text(text = short, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        measurePolicy = FirstThatFitsPolicy,
    )
}

private object FirstThatFitsPolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val (full, short) = measurables
        val whole = full.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val placeable = if (whole.width <= constraints.maxWidth) whole else short.measure(constraints.copy(minWidth = 0))
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables[0].maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables[1].minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOf { it.maxIntrinsicHeight(width) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOf { it.minIntrinsicHeight(width) }
}

/**
 * The paid badge at a glyph's bottom-right: a success circle with a black "✓", ringed in the card
 * surface so it reads apart from the glyph. 10dp on a 26dp glyph, scaled with [glyph]. Greys with
 * the glyph once the request has ended.
 */
@Composable
fun BoxScope.PaidBadge(glyph: Dp, ended: Boolean) {
    val badge = glyph * (10f / 26f)
    val ring = glyph * (1.5f / 26f)
    val surface = CodeTheme.colors.surfaceVariant.compositeOver(CodeTheme.colors.background)
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(badge + ring * 2)
            .background(surface, CircleShape)
            .padding(ring)
            .background(if (ended) CodeTheme.colors.textSecondary else CodeTheme.colors.success, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✓",
            style = CodeTheme.typography.caption.copy(fontSize = badgeFontSize(badge), lineHeight = badgeFontSize(badge)),
            color = Color.Black,
        )
    }
}

private fun badgeFontSize(badge: Dp): TextUnit = (badge.value * 0.7f).sp
