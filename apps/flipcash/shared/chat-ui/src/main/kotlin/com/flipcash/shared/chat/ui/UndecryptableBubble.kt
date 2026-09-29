package com.flipcash.shared.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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
import com.getcode.theme.CodeTheme

/**
 * What the line under an [UndecryptableBubble] tells the reader to do about it. Picked by
 * [undecryptableHint] from why the message didn't open.
 */
sealed interface UndecryptableHint {
    /** A newer version of the app can read it. */
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

@Composable
private fun UndecryptableHint.text(): String = when (this) {
    UndecryptableHint.UpdateApp -> stringResource(R.string.hint_messageUndecryptable_update)
    is UndecryptableHint.AskToResend ->
        stringResource(R.string.hint_messageUndecryptable_askToResend, firstName)
    UndecryptableHint.TrySendingAgain -> stringResource(R.string.hint_messageUndecryptable_tryAgain)
}

/**
 * A message this client can't show: a dashed bubble carrying "This message can't be displayed",
 * with a [hint] under it saying what to do about that (node 10416:1576).
 */
@Composable
fun UndecryptableBubble(
    hint: UndecryptableHint,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
) {
    val shape = RoundedCornerShape(12.dp)
    val border = Color.White.copy(alpha = 0.2f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        color = border,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - stroke,
                            size.height - stroke,
                        ),
                        cornerRadius = CornerRadius(12.dp.toPx() - stroke / 2),
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                            ),
                        ),
                    )
                }
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
            text = hint.text(),
            style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UndecryptableBubble() {
    UndecryptableBubble(hint = UndecryptableHint.UpdateApp, modifier = Modifier.padding(16.dp))
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
