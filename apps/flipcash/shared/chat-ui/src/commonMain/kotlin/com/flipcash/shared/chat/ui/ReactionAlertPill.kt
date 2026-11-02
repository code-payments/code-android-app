package com.flipcash.shared.chat.ui

import com.flipcash.core.resources.Res
import com.flipcash.core.resources.alert_boost_title
import com.flipcash.core.resources.alert_reaction_title
import com.flipcash.core.resources.alert_several_title
import com.flipcash.core.resources.alert_someone
import com.flipcash.core.resources.label_inviteLinkPreview
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.chat.alerts.ReactionAlertContent
import com.flipcash.shared.chat.alerts.ReactionAlertContent.Kind
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

private val AvatarPalette = listOf(
    Color(0xFFE8996B), Color(0xFF739EF2), Color(0xFF8CD18C), Color(0xFFD68CC8), Color(0xFFF0C864),
)

/** The iOS `AlertPill` banner's measurements, so the two platforms draw the same card. */
object ReactionAlertPillSpec {
    val MaxWidth = 378.dp
    val CornerRadius = 28.dp
    val HorizontalPadding = 18.dp
    val VerticalPadding = 14.dp
    val AvatarSize = 40.dp
    val StackAvatarSize = 32.dp

    /**
     * iOS's dark-tinted glass lands a few steps lighter than the app background (about 30 over 24),
     * not darker, so the tint is a near-black grey rather than black.
     */
    val Tint = Color(0xFF222224)
    const val TINT_ALPHA = 0.6f

    /** Used where there is no backdrop to blur: the same grey, nearly opaque. */
    const val FLAT_ALPHA = 0.94f

    val GlassStyle = HazeBlurStyle {
        blurRadius(24.dp)
        backgroundColor(Tint)
        colorEffects(listOf(HazeColorEffect.tint(Tint.copy(alpha = TINT_ALPHA))))
    }
}

/**
 * The alert banner: a dark glass card with the reactor's avatar, the chat name, the title, the
 * quoted message and the emoji or boost amount. [hazeState] is the app's blur source; without one the
 * card is a flat translucent black.
 *
 * With [animate] false the text swap on a merge is instant, for the screenshot tests.
 */
@Composable
fun ReactionAlertPill(
    content: ReactionAlertContent,
    modifier: Modifier = Modifier,
    maxWidth: Dp = ReactionAlertPillSpec.MaxWidth,
    hazeState: HazeState? = null,
    animate: Boolean = true,
) {
    val shape = RoundedCornerShape(ReactionAlertPillSpec.CornerRadius)
    val glass = if (hazeState != null) {
        Modifier.hazeBlur(HazeInput.Sources(hazeState), ReactionAlertPillSpec.GlassStyle)
    } else {
        Modifier.background(ReactionAlertPillSpec.Tint.copy(alpha = ReactionAlertPillSpec.FLAT_ALPHA))
    }
    Box(
        modifier = modifier
            .widthIn(max = minOf(ReactionAlertPillSpec.MaxWidth, maxWidth))
            .fillMaxWidth()
            .clip(shape)
            .then(glass)
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape),
    ) {
        Crossfade(
            targetState = content,
            animationSpec = tween(if (animate) 150 else 0),
            label = "alertContent",
        ) { shown ->
            AlertRow(shown)
        }
    }
}

@Composable
private fun AlertRow(content: ReactionAlertContent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ReactionAlertPillSpec.HorizontalPadding,
                vertical = ReactionAlertPillSpec.VerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (content.kind == Kind.Several) {
            AvatarStack(content)
        } else {
            AlertAvatar(content, index = 0, size = ReactionAlertPillSpec.AvatarSize, textSize = 16)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = content.chatName.orEmpty(),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Shrinks to fit before it would ellipsize: the title is never wrapped or cut.
            androidx.compose.material3.Text(
                text = title(content),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 11.sp,
                    maxFontSize = CodeTheme.typography.textSmall.fontSize,
                    stepSize = 0.5.sp,
                ),
            )
            if (content.quote.isNotEmpty()) {
                Text(
                    text = labelInviteLinks(content.quote, stringResource(Res.string.label_inviteLinkPreview)),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (content.kind) {
            Kind.Reaction -> Text(content.emojis.last(), color = CodeTheme.colors.textMain, fontSize = 30.sp, maxLines = 1)
            Kind.Boost -> Text(
                text = "+" + boostCentsText(content.boostCents ?: 0),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.success,
                maxLines = 1,
            )
            // The newest two; the title already counts everyone.
            Kind.Several -> Text(
                text = content.emojis.takeLast(2).joinToString(""),
                color = CodeTheme.colors.textMain,
                fontSize = 20.sp,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun title(content: ReactionAlertContent): String {
    val first = content.actorNames.firstOrNull() ?: stringResource(Res.string.alert_someone)
    return when (content.kind) {
        Kind.Reaction -> stringResource(Res.string.alert_reaction_title, first)
        Kind.Boost -> stringResource(Res.string.alert_boost_title, first, content.emojis.last())
        Kind.Several -> {
            val others = content.actorNames.size - 1
            pluralStringResource(Res.plurals.alert_several_title, others, first, others)
        }
    }
}

/** Smaller than a lone avatar and overlapped lightly, with the first reactor on top. */
@Composable
private fun AvatarStack(content: ReactionAlertContent) {
    val size = ReactionAlertPillSpec.StackAvatarSize
    val step = size - CodeTheme.dimens.staticGrid.x2
    val shown = content.actorNames.take(3)
    Box(modifier = Modifier.width(size + step * (shown.size - 1)).height(size)) {
        shown.indices.forEach { index ->
            AlertAvatar(
                content = content,
                index = index,
                size = size,
                textSize = 13,
                ring = true,
                modifier = Modifier
                    .offset(x = step * index)
                    .zIndex((shown.size - index).toFloat()),
            )
        }
    }
}

/** The actor's profile photo, else their initial on a palette fill. */
@Composable
private fun AlertAvatar(
    content: ReactionAlertContent,
    index: Int,
    size: Dp,
    textSize: Int,
    modifier: Modifier = Modifier,
    ring: Boolean = false,
) {
    val name = content.actorNames.getOrNull(index)
    val photo = content.actorPhotos.getOrNull(index)
    val fill = AvatarPalette[Math.floorMod(name?.hashCode() ?: index, AvatarPalette.size)]
    val ringWidth = if (ring) CodeTheme.dimens.thickBorder else 0.dp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(ringWidth)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            ContactAvatar(
                image = photo,
                displayName = name.orEmpty(),
                access = BlobAccessContext.profile(content.actorIds.getOrNull(index)),
                modifier = Modifier.size(size - ringWidth * 2),
            )
        } else {
            Text(
                text = name?.trim()?.firstOrNull()?.uppercase() ?: "?",
                color = Color.Black,
                fontSize = textSize.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
