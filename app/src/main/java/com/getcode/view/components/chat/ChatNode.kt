package com.getcode.view.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.getcode.BuildConfig
import com.getcode.LocalCurrencyUtils
import com.getcode.model.Chat
import com.getcode.model.Currency
import com.getcode.model.GenericAmount
import com.getcode.model.MessageContent
import com.getcode.model.Title
import com.getcode.model.Verb
import com.getcode.theme.CodeTheme
import com.getcode.util.DateUtils
import com.getcode.util.Kin
import com.getcode.util.formatted
import com.getcode.utils.FormatUtils
import com.getcode.view.components.Badge
import java.util.Locale

object ChatNodeDefaults {
    val UnreadIndicator: Color = Color(0xFF31BB00)
}

@Composable
fun ChatNode(
    modifier: Modifier = Modifier,
    chat: Chat,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(
                vertical = CodeTheme.dimens.grid.x3,
                horizontal = CodeTheme.dimens.inset
            ),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        val hasUnreadMessages by remember(chat.unreadCount) {
            derivedStateOf { chat.unreadCount > 0 }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = chat.localizedTitle, maxLines = 1, style = CodeTheme.typography.body1)
            chat.lastMessageMillis?.let {
                Text(
                    text = DateUtils.getDateRelatively(it),
                    style = CodeTheme.typography.body2,
                    color = if (hasUnreadMessages) ChatNodeDefaults.UnreadIndicator else CodeTheme.colors.brandLight,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = chat.messagePreview,
                style = CodeTheme.typography.body1,
                color = CodeTheme.colors.brandLight,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedVisibility(visible = hasUnreadMessages) {
                Badge(
                    count = chat.unreadCount,
                    color = ChatNodeDefaults.UnreadIndicator
                )
            }
        }
    }
}


private val Chat.localizedTitle: String
    @Composable get() {
        return title.localized
    }

val Title?.localized: String
    @Composable get() = when (val t = this) {
        is Title.Domain -> {
            t.value.capitalize(Locale.getDefault())
        }

        is Title.Localized -> {
            with(LocalContext.current) {
                val resId = resources.getIdentifier(
                    t.value,
                    "string",
                    BuildConfig.APPLICATION_ID
                ).let { if (it == 0) null else it }

                resId?.let { getString(it) }.orEmpty()
            }
        }

        else -> "Anonymous"
    }

private val Chat.messagePreview: String
    @Composable get() {
        val contents = messages.lastOrNull()?.contents ?: return "No content"

        var filtered: List<MessageContent> = contents.filterIsInstance<MessageContent.Localized>()
        if (filtered.isEmpty()) {
            filtered = contents
        }

        return filtered.map { it.localizedText }.joinToString(" ")
    }

val MessageContent.localizedText: String
    @Composable get() {
        return when (val content = this) {
            is MessageContent.Exchange -> {
                val amount = when (val kinAmount = content.amount) {
                    is GenericAmount.Exact -> {
                        val currency =
                            LocalCurrencyUtils.current?.getCurrency(kinAmount.currencyCode.name)
                        kinAmount.amount.formatted(currency = currency ?: Currency.Kin)
                    }

                    is GenericAmount.Partial -> {
                        FormatUtils.formatCurrency(kinAmount.fiat.amount, Locale.getDefault())
                    }
                }

                "You ${content.verb.toString().lowercase()} $amount"
            }

            is MessageContent.Localized -> {
                with(LocalContext.current) {
                    val resId = resources.getIdentifier(
                        content.value,
                        "string",
                        BuildConfig.APPLICATION_ID
                    ).let { if (it == 0) null else it }

                    resId?.let { getString(it) }.orEmpty()
                }
            }

            MessageContent.SodiumBox -> "<! encrypted content !>"
        }
    }