package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.ChatInputDefaults
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * The way back after a message link was followed here from another chat (concept node 11478:144,
 * Landing): "Back to {chat}" under the title bar. 36 high and fully rounded, the values node
 * 11493:201 lists for it. The ground is [JoinRequestsAnchor]'s — the composer's ultra-thin haze with
 * the divider hairline — rather than the flat white 12% the node lists: the chip floats over the
 * transcript, and over a light row (a cash card) a flat tint left the label unreadable. The flat
 * tint stays as the stand-in when there is no [HazeState] to sample.
 */
@Composable
internal fun ReturnToChatChip(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val shape = RoundedCornerShape(ChipHeight / 2)
    val ground = if (hazeState != null) {
        Modifier
            .hazeBlur(
                HazeInput.Sources(hazeState),
                HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background),
            )
            .background(ChatInputDefaults.ContainerColor)
    } else {
        Modifier.background(Color.White.copy(alpha = 0.12f))
    }
    Row(
        modifier = modifier
            .height(ChipHeight)
            .widthIn(max = ChipMaxWidth)
            .clip(shape)
            .then(ground)
            .border(CodeTheme.dimens.border, CodeTheme.colors.divider, shape)
            .clickable(onClick = onClick)
            .padding(start = CodeTheme.dimens.grid.x2, end = CodeTheme.dimens.grid.x3)
            .testTag("return_to_chat_chip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = null,
            tint = CodeTheme.colors.textMain,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.action_backToChat, title),
            style = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.SemiBold),
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ChipHeight = 36.dp
private val ChipMaxWidth = 280.dp
