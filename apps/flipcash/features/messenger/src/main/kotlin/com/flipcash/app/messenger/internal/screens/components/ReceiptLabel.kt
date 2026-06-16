package com.flipcash.app.messenger.internal.screens.components

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import kotlinx.coroutines.delay
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.getcode.theme.CodeTheme
import com.getcode.util.formatLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Composable
internal fun ReceiptLabel(
    status: ReceiptStatus,
    readPointer: MessagePointer?,
    modifier: Modifier = Modifier,
) {
    // Delayed pop for "Delivered" — wait 700ms before showing, instant removal
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(status) {
        if (status == ReceiptStatus.SENT) {
            visible = false
            delay(700.milliseconds)
            visible = true
        } else {
            visible = true
        }
    }

    val deliveredSpec = spring<Float>(dampingRatio = 0.88f, stiffness = 600f)

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(top = CodeTheme.dimens.grid.x1),
        enter = scaleIn(deliveredSpec, initialScale = 0.95f) + fadeIn(deliveredSpec),
        exit = fadeOut(snap()),
    ) {
        // Delivered -> Read directional swap with scale
        val readSwapSpec = spring<Float>(dampingRatio = 0.74f, stiffness = Spring.StiffnessHigh)
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                (scaleIn(readSwapSpec, initialScale = 0.9f) + fadeIn(readSwapSpec)) togetherWith
                    (scaleOut(readSwapSpec, targetScale = 0.9f) + fadeOut(readSwapSpec))
            },
            label = "receiptStatus",
        ) { animatedStatus ->
            val text = when (animatedStatus) {
                ReceiptStatus.SENT -> stringResource(R.string.label_chatReceipt_delivered)
                ReceiptStatus.READ -> {
                    val readAtFormatted =
                        readPointer?.timestamp?.let { formatReadTimestamp(it) } ?: ""
                    stringResource(R.string.label_chatReceipt_read, readAtFormatted)
                }

                else -> return@AnimatedContent
            }
            Text(
                text = text,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun formatReadTimestamp(instant: Instant): String {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val now = Clock.System.now()
    val tz = TimeZone.currentSystemDefault()
    val todayDate = now.toLocalDateTime(tz).date
    val readDate = instant.toLocalDateTime(tz).date

    val dayDiff = todayDate.toEpochDays() - readDate.toEpochDays()

    return when {
        // Today -> time only (e.g. "11:15 AM")
        dayDiff == 0L -> instant.formatLocalized(
            "h:mm a", is24Hour = is24Hour, if24Hour = "H:mm"
        )
        // Yesterday
        dayDiff == 1L -> stringResource(R.string.label_chatReceipt_yesterday)
        // 2-6 days ago -> weekday name (e.g. "Monday")
        dayDiff in 2L..6L -> instant.formatLocalized("EEEE")
        // Same year -> month + day (e.g. "Jun 8")
        readDate.year == todayDate.year -> instant.formatLocalized("MMM d")
        // Earlier year -> month + day + year (e.g. "Jun 8, 2025")
        else -> instant.formatLocalized("MMM d, yyyy")
    }
}

// region Previews

private fun previewPointer(timestamp: Instant) = MessagePointer(
    type = PointerType.READ,
    userId = listOf(1.toByte()),
    value = 1L,
    timestamp = timestamp,
)

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_AllStates() {
    val now = Clock.System.now()
    Column {
        ReceiptLabel(status = ReceiptStatus.SENT, readPointer = null)
        ReceiptLabel(status = ReceiptStatus.READ, readPointer = previewPointer(now))
        ReceiptLabel(status = ReceiptStatus.READ, readPointer = previewPointer(now.minus(1.days)))
        ReceiptLabel(status = ReceiptStatus.READ, readPointer = previewPointer(now.minus(3.days)))
        ReceiptLabel(status = ReceiptStatus.READ, readPointer = previewPointer(now.minus(30.days)))
        ReceiptLabel(status = ReceiptStatus.READ, readPointer = previewPointer(now.minus(400.days)))
    }
}

// endregion
