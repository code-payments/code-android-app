package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.inDisplayCurrency
import com.flipcash.app.messenger.internal.request.RequestProgressRing
import com.flipcash.app.messenger.internal.request.RequestRowFit
import com.flipcash.app.messenger.internal.request.fitRequestRow
import com.flipcash.app.messenger.internal.request.paidFractionAfterPayment
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.messenger.R
import com.flipcash.shared.chat.RequestCardState
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.requestCardState
import com.flipcash.shared.chat.ui.request.LocalPaymentRequestCards
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme

/**
 * A share payment, or a run of them for one request, as a line between two rules (node 13521:92,
 * variant Affordance=Ring; two lines, node 13534:854): "Jordan paid Alex $40 · Tahoe cabin" and
 * the request's ring as of that payment.
 *
 * [run] is newest first. The title and ring come from the request store; until the request is held
 * the row is the caption and amount alone, and it fetches the request as a card does. A request
 * that can't be shown leaves the row that way, with nothing to tap through to.
 */
@Composable
internal fun SharePaymentRow(run: List<ChatListItem.SharePayment>, modifier: Modifier = Modifier) {
    val head = run.first()
    val cards = LocalPaymentRequestCards.current
    val held = cards?.requests?.get(head.requestId)
    // The receiver stands where a card's sender does: the request must be this chat's, and the
    // person paid must be the one who made it.
    val state = cards?.let {
        requestCardState(held, head.requestId in it.missing, it.chatId, head.receiverId)
    }
    if (cards != null) {
        val absent = held == null && head.requestId !in cards.missing
        LaunchedEffect(head.requestId, absent) { if (absent) cards.onNeeded(head.requestId) }
    }
    val ready = state as? RequestCardState.Ready

    val amount = run.sumInDisplayCurrency().formatted(Fiat.FormattingRule.Truncated)
    val template = shareTemplate(run)
    val success = CodeTheme.colors.success
    val amountStyle = remember(success) { SpanStyle(color = success, fontWeight = FontWeight.SemiBold) }

    SharePaymentRowLayout(
        names = template.names,
        caption = { names -> styledTemplate(template.text, names.map { it to NameStyle } + (amount to amountStyle)) },
        title = ready?.title?.takeIf { it.isNotBlank() },
        progress = ready?.request?.split?.paidFractionAfterPayment(head.payerId, head.timestamp),
        onClick = ready?.let { { cards?.onOpen?.invoke(head.requestId) } },
        modifier = modifier,
    )
}

/** The run's amounts as the viewer reads them, added up. */
@Composable
private fun List<ChatListItem.SharePayment>.sumInDisplayCurrency(): Fiat {
    var quarks = 0L
    var currency = first().amount.currencyCode
    forEach { payment ->
        key(payment.messageId, payment.contentIndex) {
            val shown = payment.amount.inDisplayCurrency()
            quarks += shown.quarks
            currency = shown.currencyCode
        }
    }
    return Fiat(quarks, currency)
}

/** A caption's string, with the names that fill its leading `%s`; the amount fills the last. */
private class CaptionTemplate(val text: String, val names: List<String>)

@Composable
private fun shareTemplate(run: List<ChatListItem.SharePayment>): CaptionTemplate {
    val head = run.first()
    if (run.size > 1) {
        val others = run.count { !it.isFromSelf }
        return when {
            others < run.size ->
                CaptionTemplate(pluralStringResource(R.plurals.label_sharePayment_selfAndOthers, others, others, "%1\$s"), emptyList())
            head.receiverIsViewer ->
                CaptionTemplate(pluralStringResource(R.plurals.label_sharePayment_peopleToViewer, run.size, run.size, "%1\$s"), emptyList())
            else ->
                CaptionTemplate(pluralStringResource(R.plurals.label_sharePayment_people, run.size, run.size, "%1\$s"), emptyList())
        }
    }
    val receiver = head.receiverName
    return when {
        // "You" and "you" fill name slots so they take the names' weight, as the design draws them.
        head.isFromSelf ->
            CaptionTemplate(stringResource(R.string.label_sharePayment_noReceiver), listOf(stringResource(R.string.title_you)))
        head.receiverIsViewer -> CaptionTemplate(
            stringResource(R.string.label_sharePayment),
            listOf(head.payerName, stringResource(R.string.label_sharePayment_viewerAsReceiver)),
        )
        receiver == null -> CaptionTemplate(stringResource(R.string.label_sharePayment_noReceiver), listOf(head.payerName))
        else -> CaptionTemplate(stringResource(R.string.label_sharePayment), listOf(head.payerName, receiver))
    }
}

/**
 * The share payment row for given text, which is all a preview or a test has. [caption] builds the
 * caption and amount from the names as fitted; [title] and [progress] null draw neither the
 * " · title" nor the ring.
 */
@Composable
internal fun SharePaymentRowLayout(
    names: List<String>,
    caption: (names: List<String>) -> AnnotatedString,
    title: String?,
    progress: Float?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val rule = CodeTheme.colors.success.copy(alpha = RULE_ALPHA)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = SYSTEM_ROW_VERTICAL_PADDING)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(RULE_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rule(Brush.horizontalGradient(listOf(Color.Transparent, rule)))
        BoxWithConstraints(
            modifier = Modifier.widthIn(max = SYSTEM_TEXT_MAX_WIDTH),
            contentAlignment = Alignment.Center,
        ) {
            RequestRowCaption(
                names = names,
                caption = caption,
                title = title,
                progress = progress,
                maxWidth = maxWidth,
            )
        }
        Rule(Brush.horizontalGradient(listOf(rule, Color.Transparent)))
    }
}

/**
 * Fills what the caption leaves, down to [RULE_MIN_WIDTH]. Sits at the middle of the caption's
 * first line, so a caption that wraps keeps its rules beside line 1.
 */
@Composable
private fun RowScope.Rule(brush: Brush) {
    val firstLineMiddle = with(LocalDensity.current) { CodeTheme.typography.caption.lineHeight.toDp() / 2 }
    Box(
        Modifier
            .weight(1f)
            .widthIn(min = RULE_MIN_WIDTH)
            .align(Alignment.Top)
            .padding(top = firstLineMiddle)
            .height(1.dp)
            .background(brush),
    )
}

/**
 * A marked-paid or closed row: the system row's text, with names semibold but not links, since the
 * whole row taps through to Request details. A marked-paid row ends with the request's ring as of
 * that mark.
 */
@Composable
internal fun RequestNoticeRow(item: ChatListItem.RequestNotice, modifier: Modifier = Modifier) {
    val cards = LocalPaymentRequestCards.current
    val template = noticeTemplate(item)
    RequestNoticeRowLayout(
        names = template.names,
        caption = { names, title -> styledTemplate(template.text, (names + listOfNotNull(title)).mapIndexed { i, text ->
            text to NameStyle.takeIf { i < names.size }
        }) },
        // A closed row's title is part of its sentence; a marked-paid row's follows the dot.
        title = item.title,
        titleInCaption = item.kind is ChatListItem.RequestNotice.Kind.Closed,
        progress = (item.kind as? ChatListItem.RequestNotice.Kind.MarkedPaid)?.progress,
        onClick = cards?.let { { it.onOpen(item.requestId) } },
        modifier = modifier,
    )
}

@Composable
private fun noticeTemplate(item: ChatListItem.RequestNotice): CaptionTemplate {
    val creator = item.creatorName
    return when (val kind = item.kind) {
        ChatListItem.RequestNotice.Kind.Closed ->
            if (creator == null) CaptionTemplate(stringResource(R.string.label_requestClosed_byViewer), emptyList())
            else CaptionTemplate(stringResource(R.string.label_requestClosed_by), listOf(creator))

        is ChatListItem.RequestNotice.Kind.MarkedPaid -> {
            val marked = kind.marked
            val single = marked.singleOrNull()
            val others = marked.count { !it.isViewer }
            when {
                single != null && creator == null ->
                    CaptionTemplate(stringResource(R.string.label_requestMarkedPaid_byViewer), listOf(single.name))
                single != null && single.isViewer ->
                    CaptionTemplate(stringResource(R.string.label_requestMarkedPaid_viewer), listOf(creator.orEmpty()))
                single != null ->
                    CaptionTemplate(stringResource(R.string.label_requestMarkedPaid), listOf(creator.orEmpty(), single.name))
                creator == null -> CaptionTemplate(
                    pluralStringResource(R.plurals.label_requestMarkedPaid_peopleByViewer, marked.size, marked.size),
                    emptyList(),
                )
                others < marked.size -> CaptionTemplate(
                    pluralStringResource(R.plurals.label_requestMarkedPaid_viewerAndOthers, others, "%1\$s", others),
                    listOf(creator),
                )
                else -> CaptionTemplate(
                    pluralStringResource(R.plurals.label_requestMarkedPaid_people, marked.size, "%1\$s", marked.size),
                    listOf(creator),
                )
            }
        }
    }
}

@Composable
internal fun RequestNoticeRowLayout(
    names: List<String>,
    caption: (names: List<String>, title: String?) -> AnnotatedString,
    title: String,
    titleInCaption: Boolean,
    progress: Float?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = SYSTEM_ROW_VERTICAL_PADDING)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier.widthIn(max = SYSTEM_TEXT_MAX_WIDTH),
            contentAlignment = Alignment.Center,
        ) {
            if (titleInCaption) {
                // The title is an argument of the sentence, so it shortens inside it.
                RequestRowCaption(
                    names = names + title,
                    caption = { fitted -> caption(fitted.dropLast(1), fitted.last()) },
                    title = null,
                    progress = progress,
                    maxWidth = maxWidth,
                    titleAmongNames = true,
                )
            } else {
                RequestRowCaption(
                    names = names,
                    caption = { fitted -> caption(fitted, null) },
                    title = title,
                    progress = progress,
                    maxWidth = maxWidth,
                )
            }
        }
    }
}

/**
 * The text and ring both rows share, fitted to [maxWidth] by [fitRequestRow]: `<caption> · <title>
 * <ring>` on one line, or the caption over the title and ring when it can't fit.
 *
 * [titleAmongNames] says the last of [names] is a title inside the caption's sentence: it shortens
 * first and to a title's length, and moves to the second line, as a title after the dot does.
 */
@Composable
private fun RequestRowCaption(
    names: List<String>,
    caption: (names: List<String>) -> AnnotatedString,
    title: String?,
    progress: Float?,
    maxWidth: Dp,
    titleAmongNames: Boolean = false,
) {
    val style = systemRowTextStyle()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val ringSpace = if (progress != null) RING_SIZE + RING_GAP else 0.dp
    val fit = remember(names, caption, title, maxWidth, ringSpace, style, density) {
        val available = with(density) { (maxWidth - ringSpace).toPx() }
        val fullWidth = with(density) { maxWidth.toPx() }
        fun width(text: AnnotatedString) = measurer.widthOf(text, style)
        if (titleAmongNames) {
            fitRequestRow(
                names = names.dropLast(1),
                title = names.last(),
                canWrap = true,
                fits = { n, t -> width(caption(n + t.orEmpty())) <= available },
                lineOneFits = { n -> width(caption(n + "").trimmed()) <= fullWidth },
            )
        } else {
            fitRequestRow(
                names = names,
                title = title,
                canWrap = true,
                fits = { n, t -> width(oneLine(caption(n), t)) <= available },
                lineOneFits = { n -> width(caption(n)) <= fullWidth },
            )
        }
    }
    val color = CodeTheme.colors.textSecondary
    if (fit.twoLines) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The sentence without its title, when the title is one of its arguments.
            val lineOne = if (titleAmongNames) caption(fit.names + "").trimmed() else caption(fit.names)
            Text(lineOne, style = style, color = color, maxLines = 1, softWrap = false)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RING_GAP)) {
                Text(
                    text = fit.title.orEmpty(),
                    modifier = Modifier.weight(1f, fill = false),
                    style = style,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null) RowRing(progress)
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RING_GAP)) {
            Text(
                text = if (titleAmongNames) caption(fit.names + fit.title.orEmpty()) else oneLine(caption(fit.names), fit.title),
                modifier = Modifier.weight(1f, fill = false),
                style = style,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) RowRing(progress)
        }
    }
}

private fun oneLine(caption: AnnotatedString, title: String?): AnnotatedString =
    if (title == null) caption else buildAnnotatedString {
        append(caption)
        append(" · ")
        append(title)
    }

/** [this] without the blanks an empty argument leaves at either end. */
private fun AnnotatedString.trimmed(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    return if (start < 0) AnnotatedString("") else subSequence(start, text.indexOfLast { !it.isWhitespace() } + 1)
}

private fun TextMeasurer.widthOf(text: AnnotatedString, style: TextStyle): Int =
    measure(text, style = style, maxLines = 1, softWrap = false).size.width

/** The deck's ring at the row's line height, always in the open request's green: it shows a past moment. */
@Composable
private fun RowRing(progress: Float) {
    val scale = RING_SIZE / DECK_RING_SIZE
    RequestProgressRing(
        fraction = progress,
        accent = CodeTheme.colors.success,
        closed = false,
        size = RING_SIZE,
        stroke = 2.dp * scale,
        glyphStyle = CodeTheme.typography.caption.let { it.copy(fontSize = it.fontSize * scale, lineHeight = it.fontSize * scale) },
    )
}

/**
 * [template] with its `%N$s` filled from [args] in order, each in its style. Built here, not by
 * `String.format`, so each argument can be styled where the translation puts it.
 */
internal fun styledTemplate(template: String, args: List<Pair<String, SpanStyle?>>): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        PLACEHOLDER.findAll(template).forEach { match ->
            append(template, cursor, match.range.first)
            val (text, style) = args.getOrNull(match.groupValues[1].toInt() - 1) ?: (match.value to null)
            if (style != null) withStyle(style) { append(text) } else append(text)
            cursor = match.range.last + 1
        }
        append(template, cursor, template.length)
    }

private val PLACEHOLDER = Regex("""%(\d+)\$[sd]""")
private val NameStyle = SpanStyle(fontWeight = FontWeight.SemiBold)

private val RULE_GAP = 8.dp
private val RULE_MIN_WIDTH = 16.dp
private const val RULE_ALPHA = 0.28f
private val RING_SIZE = 14.dp
private val RING_GAP = 4.dp

/** The size the deck's ring is drawn for; a row's ring scales its stroke and glyph from it. */
private val DECK_RING_SIZE = 26.dp

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_RequestRows() {
    val amount = SpanStyle(color = CodeTheme.colors.success, fontWeight = FontWeight.SemiBold)
    fun caption(template: String, sum: String) = { names: List<String> ->
        styledTemplate(template, names.map { it to NameStyle } + (sum to amount))
    }
    Column(modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset)) {
        SharePaymentRowLayout(emptyList(), caption("You paid %1\$s", "$40"), "Tahoe cabin", 0.35f, {})
        SharePaymentRowLayout(listOf("Jordan"), caption("%1\$s paid you %2\$s", "$40"), "Tahoe cabin", 0.7f, {})
        SharePaymentRowLayout(listOf("Jordan", "Alex"), caption("%1\$s paid %2\$s %3\$s", "$40"), "Tahoe cabin", 1f, {})
        SharePaymentRowLayout(emptyList(), caption("7 people paid %1\$s", "$140"), "Tahoe cabin", 0.7f, {})
        SharePaymentRowLayout(listOf("Jordan", "Alex"), caption("%1\$s paid %2\$s %3\$s", "$40"), "Tahoe cabin weekend", 0.5f, {})
        SharePaymentRowLayout(listOf("Christopher", "Alex"), caption("%1\$s paid %2\$s %3\$s", "$40"), "Tahoe cabin", 0.5f, {})
        SharePaymentRowLayout(
            listOf("Maximilian", "Alexandra"), caption("%1\$s paid %2\$s %3\$s", "$12,500.00"), "Tahoe cabin weekend", 0.5f, {},
        )
        SharePaymentRowLayout(listOf("Jordan", "Alex"), caption("%1\$s paid %2\$s %3\$s", "$40"), null, null, null)
        RequestNoticeRowLayout(
            names = listOf("Alex", "Sam"),
            caption = { names, _ -> styledTemplate("%1\$s marked %2\$s as paid", names.map { it to NameStyle }) },
            title = "Tahoe cabin",
            titleInCaption = false,
            progress = 0.7f,
            onClick = {},
        )
        RequestNoticeRowLayout(
            names = listOf("Alex"),
            caption = { names, title -> styledTemplate("%1\$s closed %2\$s", (names + listOfNotNull(title)).mapIndexed { i, t -> t to NameStyle.takeIf { i == 0 } }) },
            title = "Tahoe cabin",
            titleInCaption = true,
            progress = null,
            onClick = {},
        )
        RequestNoticeRowLayout(
            names = listOf("Maximilian-Alexander"),
            caption = { names, title -> styledTemplate("%1\$s closed %2\$s", (names + listOfNotNull(title)).mapIndexed { i, t -> t to NameStyle.takeIf { i == 0 } }) },
            title = "Tahoe cabin weekend with everyone from the old office",
            titleInCaption = true,
            progress = null,
            onClick = {},
        )
    }
}
