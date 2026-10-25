package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.chat.UndecryptableReason
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import com.getcode.theme.CodeTheme

/**
 * What the line under an [UndecryptableBubble] tells the reader to do about it. Picked by
 * [undecryptableHint] from why the message didn't open.
 */
sealed interface UndecryptableHint {
    /**
     * A newer version of the app can read messages like it. This one stays hidden after an update,
     * since what was received isn't kept in a form a newer build could decode again.
     */
    data object UpdateApp : UndecryptableHint

    /** It failed to authenticate; the other person sent it and can send it again. */
    data class AskToResend(val firstName: String) : UndecryptableHint

    /** It failed to authenticate; the viewer sent it and can send it again. */
    data object TrySendingAgain : UndecryptableHint
}

/**
 * The hint for a message that arrived encrypted and didn't open, from [encryption]:
 * - an unknown scheme or content type, or no recorded outcome, is one a newer client can read;
 * - a failed authentication is the sender's to fix, so the viewer is asked to resend their own
 *   and to ask [senderName], by first name, to resend theirs.
 *
 * [senderName] is the whole name; its first word is used. Null for an opened message.
 */
fun undecryptableHint(
    encryption: MessageEncryption?,
    isFromSelf: Boolean,
    senderName: String,
): UndecryptableHint? = when (encryption) {
    is MessageEncryption.Decrypted, MessageEncryption.KeyPending -> null
    is MessageEncryption.Undecryptable -> when (encryption.reason) {
        UndecryptableReason.Unsupported -> UndecryptableHint.UpdateApp
        UndecryptableReason.Authentication -> if (isFromSelf) {
            UndecryptableHint.TrySendingAgain
        } else {
            UndecryptableHint.AskToResend(senderName.firstName())
        }
    }
    null -> UndecryptableHint.UpdateApp
}

private fun String.firstName(): String = trim().substringBefore(' ')

/**
 * The hint for [content] that arrived in the clear but that this build can't draw: a content type
 * or widget variant added after it. Null for anything it can draw.
 */
fun unsupportedContentHint(content: MessageContent): UndecryptableHint? = when (content) {
    MessageContent.Unsupported -> UndecryptableHint.UpdateApp
    is MessageContent.Widget -> when (content.widget) {
        WidgetContent.Unsupported -> UndecryptableHint.UpdateApp
        is WidgetContent.ShareProfile, is WidgetContent.PaymentRequest -> null
    }
    else -> null
}

/** The update action on the [UndecryptableHint.UpdateApp] bubble: its label and what a tap does. */
data class UpdateAppAction(val label: String, val onClick: () -> Unit)

/**
 * The update action every [UndecryptableHint.UpdateApp] bubble in a transcript shows, or null while
 * no newer store build is known. The chat screen provides it.
 */
val LocalUpdateAppAction = compositionLocalOf<UpdateAppAction?> { null }

/**
 * A message this client can't show. [UndecryptableHint.UpdateApp] draws as
 * [UnsupportedMessageBubble]; the other hints draw a dashed "This message can't be displayed"
 * bubble with the hint under it, saying what to do about that (node 10416:1576).
 *
 * [actionLabel] and [onAction] add the update action to the [UndecryptableHint.UpdateApp] bubble.
 * Leave it null until a newer store build is confirmed: the action would otherwise send someone to
 * a store page with nothing new.
 */
@Composable
fun UndecryptableBubble(
    hint: UndecryptableHint,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    when (hint) {
        UndecryptableHint.UpdateApp -> UnsupportedMessageBubble(
            modifier = modifier,
            maxWidth = maxWidth,
            actionLabel = actionLabel,
            onAction = onAction,
        )
        is UndecryptableHint.AskToResend -> HintedUndecryptableBubble(
            hint = stringResource(R.string.hint_messageUndecryptable_askToResend, hint.firstName),
            modifier = modifier,
            maxWidth = maxWidth,
        )
        UndecryptableHint.TrySendingAgain -> HintedUndecryptableBubble(
            hint = stringResource(R.string.hint_messageUndecryptable_tryAgain),
            modifier = modifier,
            maxWidth = maxWidth,
        )
    }
}

/**
 * A message that needs a newer version of the app (component 11420:13397): a title, a line asking
 * for the update, and an optional "Update Flipcash" capsule.
 *
 * Takes the incoming bubble's fill and corner rather than the design's #2B2B2B / 18, and the app's
 * type tokens rather than the design's 15 / 13 / 14, matching iOS. Keeps the design's dashed outline.
 * Title and body are separate texts so neither can clip the other.
 */
@Composable
private fun UnsupportedMessageBubble(
    maxWidth: Dp,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(BubbleDefaults.cornerLarge)
    Column(
        modifier = modifier
            .widthIn(max = maxWidth)
            .background(CodeTheme.colors.chat.incomingBubble.background, shape)
            .dashedBorder(
                color = Color.White.copy(alpha = 0.15f),
                cornerRadius = BubbleDefaults.cornerLarge,
                dash = 5.dp,
                gap = 4.dp,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.title_messageUnsupported),
            style = CodeTheme.typography.textMedium,
            color = Color.White.copy(alpha = 0.9f),
        )
        Text(
            text = stringResource(R.string.subtitle_messageUnsupported),
            style = CodeTheme.typography.caption,
            color = Color.White.copy(alpha = 0.55f),
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = CodeTheme.typography.textSmall,
                color = Color.Black,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun HintedUndecryptableBubble(
    hint: String,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = maxWidth),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .dashedBorder(color = Color.White.copy(alpha = 0.2f), cornerRadius = 12.dp, dash = 4.dp, gap = 3.dp)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(R.drawable.ic_message_undecryptable),
                contentDescription = null,
                // The exported glyph carries its own colour.
                tint = Color.Unspecified,
            )
            Text(
                text = stringResource(R.string.label_messageUndecryptable),
                style = CodeTheme.typography.textMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp,
                ),
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Text(
            modifier = Modifier.padding(start = 4.dp),
            text = hint,
            style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

/** A 1dp dashed stroke drawn just inside the bounds, on a rounded rect of [cornerRadius]. */
private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, dash: Dp, gap: Dp): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(cornerRadius.toPx() - stroke / 2),
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
            ),
        )
    }

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UndecryptableBubble() {
    UndecryptableBubble(
        hint = UndecryptableHint.UpdateApp,
        modifier = Modifier.padding(16.dp),
        maxWidth = 258.dp,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UndecryptableBubble_UpdateAction() {
    UndecryptableBubble(
        hint = UndecryptableHint.UpdateApp,
        modifier = Modifier.padding(16.dp),
        maxWidth = 258.dp,
        actionLabel = stringResource(R.string.action_updateFlipcash),
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UndecryptableBubble_AskToResend() {
    UndecryptableBubble(
        hint = UndecryptableHint.AskToResend("Ted"),
        modifier = Modifier.padding(16.dp),
    )
}
