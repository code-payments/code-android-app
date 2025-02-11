package com.getcode.ui.components.chat.messagecontents

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import com.getcode.extensions.formattedRaw
import com.getcode.model.sum
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.components.chat.utils.MessageTip

@Composable
internal fun Tips(
    tips: List<MessageTip>,
    isMessageFromSelf: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (tips.isNotEmpty()) {
        val didUserTip = tips.any { it.tipper.isSelf }
        val backgroundColor by animateColorAsState(
            when {
                isMessageFromSelf -> CodeTheme.colors.surface
                didUserTip -> CodeTheme.colors.tertiary
                else -> Color.Transparent
            }
        )

        val borderColor by animateColorAsState(
            when {
                !didUserTip && !isMessageFromSelf -> CodeTheme.colors.tertiary
                else -> Color.Transparent
            }
        )

        val contentColor by animateColorAsState(
            when {
                isMessageFromSelf -> CodeTheme.colors.onBackground
                didUserTip -> CodeTheme.colors.onBackground
                else -> CodeTheme.colors.onBackground
            }
        )

        val totalTips = tips.map { it.amount }.sum().formattedRaw()

        Row(
            modifier = modifier
                .clip(CircleShape)
                .clickable { onClick() }
                .background(backgroundColor, CircleShape)
                .border(CodeTheme.dimens.border, borderColor, CircleShape)
                .padding(
                    horizontal = CodeTheme.dimens.grid.x2,
                    vertical = CodeTheme.dimens.grid.x1,
                ),
            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
        ) {
            Text(
                text = stringResource(R.string.title_kinAmountWithLogo, totalTips),
                color = contentColor,
                style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.W700),
            )

//            TipperDisplay(tips, contentColor, modifier)
        }
    }
}

@Composable
internal fun TipperDisplay(
    tips: List<MessageTip>,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-8).dp)
    ) {
        val imageModifier = Modifier
            .size(CodeTheme.dimens.staticGrid.x4)
            .clip(CircleShape)
            .border(CodeTheme.dimens.border, contentColor, CircleShape)

        val tippers = remember(tips) { tips.map { it.tipper }.distinct() }

        tippers.take(3).fastForEachIndexed { index, tipper ->
            UserAvatar(
                modifier = imageModifier
                    .zIndex((tips.size - index).toFloat()),
                data = tipper.profileImage.nullIfEmpty() ?: tipper.id
            )
        }
    }
}

private fun String?.nullIfEmpty() = if (this?.isEmpty() == true) null else this

